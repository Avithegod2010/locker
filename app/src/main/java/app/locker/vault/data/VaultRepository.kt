package app.locker.vault.data

import android.content.Context
import android.net.Uri
import app.locker.vault.core.AppPreferences
import app.locker.vault.core.Crypto
import app.locker.vault.core.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Encrypts/decrypts domain objects and keeps a small in-memory projection for the UI.
 * Every read/write happens off the main thread.
 */
class VaultRepository(
    context: Context,
    private val db: VaultDb,
    private val files: EncryptedFileStore,
    private val session: SessionManager,
    private val preferences: AppPreferences
) {
    private val appContext = context.applicationContext
    private val writeMutex = Mutex()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _collections = MutableStateFlow<List<VaultCollection>>(emptyList())
    val collections: StateFlow<List<VaultCollection>> = _collections.asStateFlow()

    private val _legacyContacts = MutableStateFlow<List<LegacyContact>>(emptyList())
    val legacyContacts: StateFlow<List<LegacyContact>> = _legacyContacts.asStateFlow()

    private val _profile = MutableStateFlow(VaultProfile())
    val profile: StateFlow<VaultProfile> = _profile.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val key = session.masterKeyOrNull() ?: return@withContext
        try {
            writeMutex.withLock {
                reload(key)
            }
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun seedDefaults() {
        val key = session.masterKeyOrNull() ?: return
        try {
            writeMutex.withLock {
                if (db.readCollections().isEmpty()) {
                    addCollectionLocked(key, "Documents", 0xFF57D9A3, "folder")
                    reload(key)
                }
            }
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun createTextItem(
        type: VaultType,
        title: String,
        body: String = "",
        username: String = "",
        password: String = "",
        location: String = "",
        website: String = "",
        otpSecret: String = "",
        tags: List<String> = emptyList(),
        fields: List<CustomField> = emptyList(),
        collectionIds: List<String> = emptyList(),
        favorite: Boolean = false,
        parentId: String = ""
    ): Result<VaultItem> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val now = System.currentTimeMillis()
                val item = VaultItem(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = title.trim().ifBlank { defaultTitle(type) },
                    body = body,
                    username = username,
                    password = password,
                    location = location,
                    website = website,
                    otpSecret = otpSecret.trim(),
                    tags = cleanTags(tags),
                    fields = cleanFields(fields),
                    favorite = favorite,
                    collectionIds = collectionIds.distinct(),
                    parentId = parentId,
                    createdAt = now,
                    updatedAt = now
                )
                writeMutex.withLock {
                    upsertItemLocked(key, item)
                    reload(key)
                }
                item
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun updateItem(item: VaultItem): Result<VaultItem> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val updated = item.copy(
                    title = item.title.trim().ifBlank { defaultTitle(item.type) },
                    tags = cleanTags(item.tags),
                    fields = cleanFields(item.fields),
                    collectionIds = item.collectionIds.distinct(),
                    updatedAt = System.currentTimeMillis()
                )
                writeMutex.withLock {
                    upsertItemLocked(key, updated)
                    reload(key)
                }
                updated
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    /** Updates only collection membership, preserving any concurrent field edits. */
    suspend fun setCollections(itemId: String, collectionIds: List<String>): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val row = db.readItems().firstOrNull { it.id == itemId }
                        ?: error("Item no longer exists")
                    val current = decodeItem(row, key) ?: error("Could not read item")
                    upsertItemLocked(
                        key,
                        current.copy(
                            collectionIds = collectionIds.distinct(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun importFile(
        uri: Uri,
        type: VaultType,
        title: String,
        collectionIds: List<String>,
        favorite: Boolean = false,
        parentId: String = ""
    ): Result<VaultItem> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val info = files.describe(uri)
                val id = UUID.randomUUID().toString()
                val imported = files.importFile(
                    uri = uri,
                    itemId = id,
                    mimeType = info.mimeType,
                    masterKey = key,
                    generateThumbnail = info.mimeType.startsWith("image/")
                )
                val ocrText = appContext.let { context ->
                    app.locker.vault.core.Ocr.recognize(context, uri, info.mimeType).orEmpty()
                }
                val duplicate = _items.value.firstOrNull {
                    it.id != id && it.contentHash.equals(imported.contentHash, ignoreCase = true) &&
                        imported.contentHash.isNotBlank()
                }
                val now = System.currentTimeMillis()
                val item = VaultItem(
                    id = id,
                    type = type,
                    title = title.trim().ifBlank { info.displayName.ifBlank { defaultTitle(type) } },
                    fileName = info.displayName,
                    mimeType = info.mimeType,
                    sizeBytes = imported.sizeBytes,
                    wrappedFileKey = imported.wrappedFileKey,
                    hasThumbnail = imported.hasThumbnail,
                    contentHash = imported.contentHash,
                    ocrText = ocrText,
                    favorite = favorite,
                    tags = buildList {
                        if (info.displayName.contains("screenshot", ignoreCase = true)) {
                            add("screenshot")
                        }
                        if (duplicate != null) add("duplicate")
                    },
                    collectionIds = collectionIds.distinct(),
                    parentId = parentId,
                    createdAt = now,
                    updatedAt = now
                )
                try {
                    writeMutex.withLock {
                        upsertItemLocked(key, item)
                        reload(key)
                    }
                    item
                } catch (t: Throwable) {
                    files.deleteItemFiles(id)
                    throw t
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun toggleFavorite(item: VaultItem): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val updated = item.copy(favorite = !item.favorite)
                writeMutex.withLock {
                    upsertItemLocked(key, updated)
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun bulkSetFavorite(items: List<VaultItem>, favorite: Boolean): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    items.forEach { item ->
                        upsertItemLocked(
                            key,
                            item.copy(favorite = favorite, updatedAt = System.currentTimeMillis())
                        )
                    }
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun bulkMove(items: List<VaultItem>, collectionIds: List<String>): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    items.forEach { item ->
                        upsertItemLocked(
                            key,
                            item.copy(
                                collectionIds = collectionIds.distinct(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun bulkDelete(items: List<VaultItem>): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val now = System.currentTimeMillis()
                    items.forEach { item ->
                        db.setItemDeleted(item.id, now)
                        childrenOf(item.id).forEach { child ->
                            db.setItemDeleted(child.id, now)
                        }
                    }
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun bulkAddTag(items: List<VaultItem>, tag: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val cleaned = cleanTags(listOf(tag)).firstOrNull() ?: return@withContext
                writeMutex.withLock {
                    items.forEach { item ->
                        upsertItemLocked(
                            key,
                            item.copy(
                                tags = (item.tags + cleaned).distinctBy { it.lowercase() },
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun softDelete(item: VaultItem): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val now = System.currentTimeMillis()
                    db.setItemDeleted(item.id, now)
                    childrenOf(item.id).forEach { child ->
                        db.setItemDeleted(child.id, now)
                    }
                    reload(key)
                }
                files.clearShareCacheFor(item.id)
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun archive(item: VaultItem): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    upsertItemLocked(
                        key,
                        item.copy(
                            archivedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun unarchive(item: VaultItem): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    upsertItemLocked(
                        key,
                        item.copy(
                            archivedAt = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun restore(item: VaultItem): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val restored = item.copy(
                    deletedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                writeMutex.withLock {
                    upsertItemLocked(key, restored)
                    db.setItemDeleted(item.id, null)
                    childrenOf(item.id).forEach { child ->
                        val childRestored = child.copy(
                            deletedAt = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        upsertItemLocked(key, childRestored)
                        db.setItemDeleted(child.id, null)
                    }
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun deleteForever(item: VaultItem): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    childrenOf(item.id).forEach { child ->
                        files.deleteItemFiles(child.id)
                        db.deleteItem(child.id)
                    }
                    files.deleteItemFiles(item.id)
                    db.deleteItem(item.id)
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun emptyTrash(): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val rows = db.readItems().filter { it.deletedAt != null }
                    rows.forEach {
                        files.deleteItemFiles(it.id)
                        db.deleteItem(it.id)
                    }
                    reload(key)
                    rows.size
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun purgeExpiredTrash(retentionDays: Int = 30): Int {
        if (!preferences.settings.value.autoPurgeTrash) return 0
        return withContext(Dispatchers.IO) {
            val key = session.masterKeyOrNull() ?: return@withContext 0
            try {
                writeMutex.withLock {
                    val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
                    val expired = db.readItems().filter { (it.deletedAt ?: Long.MAX_VALUE) < cutoff }
                    expired.forEach {
                        files.deleteItemFiles(it.id)
                        db.deleteItem(it.id)
                    }
                    if (expired.isNotEmpty()) reload(key)
                    expired.size
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun readThumbnail(item: VaultItem): ByteArray? {
        if (!item.hasThumbnail) return null
        val key = session.requireMasterKey()
        return try {
            files.readBytes(key, item, thumbnail = true, maxBytes = 4L * 1024 * 1024)
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun readImage(item: VaultItem): ByteArray? {
        val key = session.requireMasterKey()
        return try {
            files.readBytes(key, item, thumbnail = false)
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun decryptToShareCache(item: VaultItem): java.io.File? {
        val key = session.requireMasterKey()
        return try {
            files.decryptToShareCache(key, item)
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun exportFile(item: VaultItem, output: java.io.OutputStream): Boolean {
        val key = session.requireMasterKey()
        return try {
            files.exportToStream(key, item, output)
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun addCollection(name: String, color: Long, icon: String): Result<VaultCollection> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val collection = addCollectionLocked(key, name.trim(), color, icon)
                    reload(key)
                    collection
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun updateCollection(collection: VaultCollection): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    db.insertCollection(
                        collection.id,
                        collection.createdAt,
                        Crypto.encrypt(key, encodeCollection(collection), collectionAad(collection.id))
                    )
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun deleteCollection(collectionId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val rows = db.readItems()
                    rows.forEach { row ->
                        val item = decodeItem(row, key) ?: return@forEach
                        if (collectionId in item.collectionIds) {
                            upsertItemLocked(
                                key,
                                item.copy(
                                    collectionIds = item.collectionIds - collectionId,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    db.deleteCollection(collectionId)
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun addLegacyContact(
        name: String,
        relationship: String,
        contact: String,
        notes: String
    ): Result<LegacyContact> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val now = System.currentTimeMillis()
                val entry = LegacyContact(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    relationship = relationship.trim(),
                    contact = contact.trim(),
                    notes = notes.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                writeMutex.withLock {
                    upsertLegacyLocked(key, entry)
                    reload(key)
                }
                entry
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun updateLegacyContact(entry: LegacyContact): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val updated = entry.copy(updatedAt = System.currentTimeMillis())
                writeMutex.withLock {
                    upsertLegacyLocked(key, updated)
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun deleteLegacyContact(id: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    db.deleteLegacyContact(id)
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun saveProfile(name: String, email: String): Result<VaultProfile> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                val profile = VaultProfile(
                    name = name.trim().ifBlank { "Locker" },
                    email = email.trim().ifBlank { "Local vault" }
                )
                writeMutex.withLock {
                    db.saveProfile(
                        Crypto.encrypt(
                            key,
                            JSONObject()
                                .put("name", profile.name)
                                .put("email", profile.email)
                                .toString()
                                .toByteArray(Charsets.UTF_8),
                            "locker:profile:me".toByteArray()
                        )
                    )
                    reload(key)
                }
                profile
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            db.wipeAndClose()
            files.wipeAll()
            _items.value = emptyList()
            _collections.value = emptyList()
            _legacyContacts.value = emptyList()
            _profile.value = VaultProfile()
        }
    }

    fun clearInMemory() {
        _items.value = emptyList()
        _collections.value = emptyList()
        _legacyContacts.value = emptyList()
        _profile.value = VaultProfile()
    }

    fun findItem(id: String): VaultItem? = _items.value.firstOrNull { it.id == id }

    fun childrenOf(parentId: String): List<VaultItem> =
        _items.value.filter { it.parentId == parentId }

    fun allTags(): List<String> = _items.value
        .flatMap { it.tags }
        .distinct()
        .sorted()

    suspend fun setTags(itemId: String, tags: List<String>): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val key = session.requireMasterKey()
            try {
                writeMutex.withLock {
                    val row = db.readItems().firstOrNull { it.id == itemId }
                        ?: error("Item no longer exists")
                    val current = decodeItem(row, key) ?: error("Could not read item")
                    upsertItemLocked(
                        key,
                        current.copy(tags = cleanTags(tags), updatedAt = System.currentTimeMillis())
                    )
                    reload(key)
                }
            } finally {
                Crypto.wipe(key)
            }
        }
    }

    private fun cleanTags(tags: List<String>): List<String> = tags
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(50)

    private fun cleanFields(fields: List<CustomField>): List<CustomField> = fields
        .filter { it.label.isNotBlank() }
        .distinctBy { it.label.lowercase() }
        .take(100)

    private fun reload(key: ByteArray) {
        // An in-flight write may finish after the vault locks. Never repopulate the
        // decrypted in-memory projection while the app is on the lock screen.
        if (session.state.value == app.locker.vault.core.SessionState.LOCKED) return
        _items.value = db.readItems().mapNotNull { decodeItem(it, key) }
        _collections.value = db.readCollections().mapNotNull { decodeCollection(it, key) }
            .sortedBy { it.createdAt }
        _legacyContacts.value = db.readLegacyContacts().mapNotNull { decodeLegacy(it, key) }
            .sortedBy { it.createdAt }
        _profile.value = decodeProfile(db.readProfile(), key) ?: VaultProfile()
    }

    private fun decodeProfile(payload: ByteArray?, key: ByteArray): VaultProfile? {
        if (payload == null) return null
        return try {
            val plaintext = Crypto.decrypt(key, payload, "locker:profile:me".toByteArray()) ?: return null
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            VaultProfile(
                name = json.optString("name", "Locker"),
                email = json.optString("email", "Local vault")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun upsertItemLocked(key: ByteArray, item: VaultItem) {
        val payload = Crypto.encrypt(
            key,
            encodeItem(item),
            itemAad(item.id, item.type)
        )
        db.upsertItem(
            id = item.id,
            type = item.type.name,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            deletedAt = item.deletedAt,
            payload = payload
        )
        if (item.deletedAt != null) {
            db.setItemDeleted(item.id, item.deletedAt)
        }
    }

    private fun addCollectionLocked(
        key: ByteArray,
        name: String,
        color: Long,
        icon: String
    ): VaultCollection {
        val collection = VaultCollection(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Collection" },
            color = color,
            icon = icon
        )
        db.insertCollection(
            collection.id,
            collection.createdAt,
            Crypto.encrypt(key, encodeCollection(collection), collectionAad(collection.id))
        )
        return collection
    }

    private fun upsertLegacyLocked(key: ByteArray, entry: LegacyContact) {
        db.upsertLegacyContact(
            entry.id,
            entry.createdAt,
            entry.updatedAt,
            Crypto.encrypt(key, encodeLegacy(entry), legacyAad(entry.id))
        )
    }

    private fun decodeItem(row: ItemRow, key: ByteArray): VaultItem? {
        return try {
            val plaintext = Crypto.decrypt(
                key,
                row.payload,
                itemAad(row.id, VaultType.valueOf(row.type))
            ) ?: return null
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            val collections = mutableListOf<String>()
            json.optJSONArray("collections")?.let { array ->
                for (i in 0 until array.length()) {
                    collections += array.optString(i)
                }
            }
            val tags = mutableListOf<String>()
            json.optJSONArray("tags")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(tags::add)
                }
            }
            val fields = mutableListOf<CustomField>()
            json.optJSONArray("fields")?.let { array ->
                for (i in 0 until array.length()) {
                    val field = array.optJSONObject(i) ?: continue
                    val label = field.optString("label")
                    if (label.isNotBlank()) {
                        fields += CustomField(
                            label = label,
                            value = field.optString("value"),
                            sensitive = field.optBoolean("sensitive", false)
                        )
                    }
                }
            }
            VaultItem(
                id = row.id,
                type = VaultType.valueOf(row.type),
                title = json.optString("title"),
                body = json.optString("body"),
                username = json.optString("username"),
                password = json.optString("password"),
                location = json.optString("location"),
                website = json.optString("website"),
                otpSecret = json.optString("otpSecret"),
                ocrText = json.optString("ocrText"),
                fileName = json.optString("fileName"),
                mimeType = json.optString("mimeType"),
                sizeBytes = json.optLong("sizeBytes", 0),
                wrappedFileKey = json.optString("fileKey").takeIf { it.isNotBlank() },
                hasThumbnail = json.optBoolean("hasThumbnail", false),
                contentHash = json.optString("contentHash"),
                favorite = json.optBoolean("favorite", false),
                tags = tags,
                fields = fields,
                collectionIds = collections,
                parentId = json.optString("parentId"),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                archivedAt = json.optLong("archivedAt", 0L).takeIf { it > 0L },
                deletedAt = row.deletedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeCollection(row: NameRow, key: ByteArray): VaultCollection? {
        return try {
            val plaintext = Crypto.decrypt(key, row.payload, collectionAad(row.id)) ?: return null
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            VaultCollection(
                id = row.id,
                name = json.optString("name"),
                color = json.optLong("color", 0xFF5B9CFF),
                icon = json.optString("icon", "folder"),
                createdAt = row.createdAt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeLegacy(row: NameRow, key: ByteArray): LegacyContact? {
        return try {
            val plaintext = Crypto.decrypt(key, row.payload, legacyAad(row.id)) ?: return null
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            LegacyContact(
                id = row.id,
                name = json.optString("name"),
                relationship = json.optString("relationship"),
                contact = json.optString("contact"),
                notes = json.optString("notes"),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeItem(item: VaultItem): ByteArray {
        val json = JSONObject()
            .put("title", item.title)
            .put("body", item.body)
            .put("username", item.username)
            .put("password", item.password)
            .put("location", item.location)
            .put("website", item.website)
            .put("otpSecret", item.otpSecret)
            .put("ocrText", item.ocrText)
            .put("fileName", item.fileName)
            .put("mimeType", item.mimeType)
            .put("sizeBytes", item.sizeBytes)
            .put("fileKey", item.wrappedFileKey ?: "")
            .put("hasThumbnail", item.hasThumbnail)
            .put("contentHash", item.contentHash)
            .put("favorite", item.favorite)
            .put("archivedAt", item.archivedAt ?: 0L)
            .put("tags", JSONArray(item.tags))
            .put("parentId", item.parentId)
            .put(
                "fields",
                JSONArray().apply {
                    item.fields.forEach { field ->
                        put(
                            JSONObject()
                                .put("label", field.label)
                                .put("value", field.value)
                                .put("sensitive", field.sensitive)
                        )
                    }
                }
            )
            .put("collections", JSONArray(item.collectionIds))
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encodeCollection(collection: VaultCollection): ByteArray =
        JSONObject()
            .put("name", collection.name)
            .put("color", collection.color)
            .put("icon", collection.icon)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private fun encodeLegacy(entry: LegacyContact): ByteArray =
        JSONObject()
            .put("name", entry.name)
            .put("relationship", entry.relationship)
            .put("contact", entry.contact)
            .put("notes", entry.notes)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private fun itemAad(id: String, type: VaultType): ByteArray =
        "locker:item:$id:${type.name}".toByteArray()

    private fun collectionAad(id: String): ByteArray = "locker:collection:$id".toByteArray()

    private fun legacyAad(id: String): ByteArray = "locker:legacy:$id".toByteArray()

    private fun defaultTitle(type: VaultType): String = when (type) {
        VaultType.SECRET -> "Untitled secret"
        VaultType.NOTE -> "Untitled note"
        VaultType.THING -> "Untitled thing"
        VaultType.FILE -> "Untitled file"
        VaultType.PHOTO -> "Untitled photo"
    }
}
