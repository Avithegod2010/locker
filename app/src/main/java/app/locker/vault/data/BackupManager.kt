package app.locker.vault.data

import android.content.Context
import android.net.Uri
import app.locker.vault.core.Crypto
import app.locker.vault.core.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupPreview(
    val createdAt: Long,
    val itemCount: Int,
    val collectionCount: Int,
    val legacyCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
    val matchesCurrentVault: Boolean
)

data class BackupSummary(
    val itemsRestored: Int,
    val collectionsRestored: Int,
    val filesRestored: Int,
    val replaced: Boolean
)

enum class RestoreMode { REPLACE, MERGE }

/**
 * Streaming `.locker` backup format.
 *
 * Header: magic | format | KDF version | salt | wrapped master key | encrypted manifest |
 *         record nonce prefix | creation time
 * Body:   authenticated records (metadata JSON or raw encrypted file blobs) | authenticated end hash
 *
 * The backup is encrypted with a key derived from a user-chosen backup password. The vault
 * master key is wrapped inside that header, so a restore can verify the backup belongs to
 * the currently unlocked vault before changing anything.
 */
class BackupManager(
    context: Context,
    private val db: VaultDb,
    private val files: EncryptedFileStore,
    private val repository: VaultRepository,
    private val session: SessionManager
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun createBackup(
        uri: Uri,
        password: CharArray,
        onProgress: (Float) -> Unit = {}
    ): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            if (password.size < 10) error("Use at least 10 characters for the backup password")
            val master = session.requireMasterKey()
            val backupKey = Crypto.randomBytes(Crypto.MASTER_KEY_BYTES)
            val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
            val derived = try {
                Crypto.deriveKey(password, salt, Crypto.currentKdfParams)
            } finally {
                password.fill('\u0000')
            }
            try {
                val wrappedMaster = Crypto.encrypt(
                    derived,
                    master,
                    AAD_BACKUP_MASTER
                )
                val items = db.readItems()
                val collections = db.readCollections()
                val legacy = db.readLegacyContacts()
                val profile = db.readProfile()
                val fileEntries = items.mapNotNull { row ->
                    val file = files.rawFile(row.id).takeIf { it.exists() }
                    val thumb = files.rawThumbnail(row.id).takeIf { it.exists() }
                    if (file == null && thumb == null) null else Triple(row.id, file, thumb)
                }
                val totalBytes = fileEntries.sumOf { (_, file, thumb) ->
                    (file?.length() ?: 0L) + (thumb?.length() ?: 0L)
                }
                val now = System.currentTimeMillis()
                val manifest = JSONObject()
                    .put("createdAt", now)
                    .put("itemCount", items.size)
                    .put("collectionCount", collections.size)
                    .put("legacyCount", legacy.size)
                    .put("fileCount", fileEntries.count { it.second != null })
                    .put("thumbCount", fileEntries.count { it.third != null })
                    .put("totalBytes", totalBytes)
                    .put("vaultName", repository.profile.value.name)
                    .put("format", FORMAT_VERSION)
                val manifestBlob = Crypto.encrypt(
                    derived,
                    manifest.toString().toByteArray(Charsets.UTF_8),
                    AAD_BACKUP_MANIFEST
                )
                val prefix = Crypto.randomBytes(4)

                val output = resolver.openOutputStream(uri)
                    ?: error("Could not open the selected destination")
                DataOutputStream(BufferedOutputStream(output)).use { out ->
                    out.write(MAGIC)
                    out.writeInt(FORMAT_VERSION)
                    out.writeInt(Crypto.kdfVersion)
                    out.writeBytesCompat(salt)
                    out.writeBytesCompat(wrappedMaster)
                    out.writeBytesCompat(manifestBlob)
                    out.write(prefix)
                    out.writeLong(now)

                    val writer = RecordWriter(out, derived, prefix)
                    collections.forEachIndexed { index, row ->
                        writer.writeBytes(TYPE_COLLECTION, row.id, encodeCollectionRow(row))
                        onProgress(0.05f + 0.15f * (index + 1) / collections.size.coerceAtLeast(1))
                    }
                    legacy.forEach { row ->
                        writer.writeBytes(TYPE_LEGACY, row.id, encodeNameRow(row))
                    }
                    if (profile != null) {
                        writer.writeBytes(
                            TYPE_PROFILE,
                            "profile",
                            JSONObject().put("payload", Crypto.b64encode(profile)).toString()
                                .toByteArray(Charsets.UTF_8)
                        )
                    }
                    items.forEachIndexed { index, row ->
                        writer.writeBytes(TYPE_ITEM, row.id, encodeItemRow(row))
                        onProgress(0.2f + 0.35f * (index + 1) / items.size.coerceAtLeast(1))
                    }
                    fileEntries.forEachIndexed { index, (id, file, thumb) ->
                        if (file != null) {
                            file.inputStream().buffered().use { input ->
                                writer.write(TYPE_FILE, id, input, file.length())
                            }
                        }
                        if (thumb != null) {
                            thumb.inputStream().buffered().use { input ->
                                writer.write(TYPE_THUMB, id, input, thumb.length())
                            }
                        }
                        onProgress(0.55f + 0.4f * (index + 1) / fileEntries.size.coerceAtLeast(1))
                    }
                    writer.writeEnd()
                    onProgress(1f)
                }
                BackupPreview(
                    createdAt = now,
                    itemCount = items.size,
                    collectionCount = collections.size,
                    legacyCount = legacy.size,
                    fileCount = fileEntries.count { it.second != null },
                    totalBytes = totalBytes,
                    matchesCurrentVault = true
                )
            } finally {
                Crypto.wipe(master, derived, backupKey)
            }
        }
    }

    suspend fun inspect(
        uri: Uri,
        password: CharArray
    ): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val input = resolver.openInputStream(uri)?.buffered()
                ?: error("Could not open the backup")
            DataInputStream(input).use { data ->
                val header = readHeader(data, password)
                try {
                    val manifest = header.manifest
                    val current = session.masterKeyOrNull()
                    val matches = current?.let { Crypto.isEqual(it, header.masterKey) } ?: false
                    current?.let { Crypto.wipe(it) }
                    BackupPreview(
                        createdAt = manifest.optLong("createdAt", 0L),
                        itemCount = manifest.optInt("itemCount", 0),
                        collectionCount = manifest.optInt("collectionCount", 0),
                        legacyCount = manifest.optInt("legacyCount", 0),
                        fileCount = manifest.optInt("fileCount", 0),
                        totalBytes = manifest.optLong("totalBytes", 0L),
                        matchesCurrentVault = matches
                    )
                } finally {
                    header.wipe()
                }
            }
        }
    }

    suspend fun restore(
        uri: Uri,
        password: CharArray,
        mode: RestoreMode,
        onProgress: (Float) -> Unit = {}
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val currentMaster = session.requireMasterKey()
            val input = resolver.openInputStream(uri)?.buffered()
                ?: error("Could not open the backup")
            val restoreDir = File(appContext.filesDir, "restore_tmp").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                DataInputStream(input).use { data ->
                    val header = readHeader(data, password)
                    try {
                        if (!Crypto.isEqual(currentMaster, header.masterKey)) {
                            error("This backup belongs to a different vault")
                        }
                        val items = mutableListOf<ItemRow>()
                        val collections = mutableListOf<NameRow>()
                        val legacy = mutableListOf<NameRow>()
                        var profile: ByteArray? = null
                        val stagedFiles = mutableMapOf<String, File>()
                        var verified = false
                        val reader = RecordReader(data, header.backupKey, header.prefix)
                        var records = 0
                        while (true) {
                            val record = reader.readHeader()
                            when (record.type) {
                                TYPE_COLLECTION -> {
                                    val bytes = reader.readBytes(MAX_METADATA)
                                    collections += decodeCollectionRow(record.id, bytes)
                                }
                                TYPE_LEGACY -> {
                                    val bytes = reader.readBytes(MAX_METADATA)
                                    legacy += decodeNameRow(record.id, bytes)
                                }
                                TYPE_PROFILE -> {
                                    val bytes = reader.readBytes(MAX_METADATA)
                                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                                    profile = json.optString("payload").let(Crypto::b64decode)
                                }
                                TYPE_ITEM -> {
                                    val bytes = reader.readBytes(MAX_METADATA)
                                    items += decodeItemRow(record.id, bytes)
                                }
                                TYPE_FILE, TYPE_THUMB -> {
                                    val target = File(
                                        restoreDir,
                                        "${record.type}_${safeId(record.id)}.bin"
                                    )
                                    target.outputStream().buffered().use { output ->
                                        reader.readTo(output)
                                    }
                                    stagedFiles["${record.type}:${record.id}"] = target
                                }
                                TYPE_END -> {
                                    val expected = reader.readBytes(256, trackDigest = false)
                                    val actual = reader.digestHex()
                                    if (String(expected, Charsets.UTF_8) != actual) {
                                        error("Backup checksum failed; the file is damaged")
                                    }
                                    verified = true
                                    break
                                }
                                else -> error("Unknown backup record")
                            }
                            records++
                            if (records % 25 == 0) onProgress((records / (records + 25f)).coerceIn(0f, 0.9f))
                        }
                        if (!verified) error("Backup ended unexpectedly")
                        if (items.size != header.manifest.optInt("itemCount", items.size)) {
                            error("Backup manifest does not match its contents")
                        }

                        val existingItems = db.readItems().associateBy { it.id }
                        val existingCollections = db.readCollections().associateBy { it.id }
                        val existingLegacy = db.readLegacyContacts().associateBy { it.id }
                        if (mode == RestoreMode.REPLACE) {
                            db.clearAll()
                            files.wipeAll()
                        }
                        var itemsRestored = 0
                        val restoredIds = mutableSetOf<String>()
                        items.forEach { row ->
                            val existing = existingItems[row.id]
                            if (mode == RestoreMode.REPLACE || existing == null ||
                                row.updatedAt > existing.updatedAt
                            ) {
                                db.insertItemRow(row)
                                itemsRestored++
                                restoredIds += row.id
                            }
                        }
                        var collectionsRestored = 0
                        collections.forEach { row ->
                            if (mode == RestoreMode.REPLACE || existingCollections[row.id] == null) {
                                db.insertCollectionRow(row)
                                collectionsRestored++
                            }
                        }
                        legacy.forEach { row ->
                            if (mode == RestoreMode.REPLACE || existingLegacy[row.id] == null) {
                                db.insertLegacyRow(row)
                            }
                        }
                        if (profile != null && (mode == RestoreMode.REPLACE || db.readProfile() == null)) {
                            db.saveProfile(profile)
                        }

                        var filesRestored = 0
                        stagedFiles.forEach { (key, temp) ->
                            val parts = key.split(':', limit = 2)
                            val type = parts[0].toIntOrNull() ?: return@forEach
                            val id = parts.getOrNull(1) ?: return@forEach
                            val itemWasRestored = id in restoredIds || mode == RestoreMode.REPLACE
                            val target = if (type == TYPE_FILE) files.rawFile(id) else files.rawThumbnail(id)
                            if (itemWasRestored || !target.exists()) {
                                temp.inputStream().buffered().use { input ->
                                    if (type == TYPE_FILE) {
                                        files.writeRawFile(id, input)
                                    } else {
                                        files.writeRawThumbnail(id, input)
                                    }
                                }
                                filesRestored++
                            }
                        }
                        onProgress(1f)
                        repository.refresh()
                        BackupSummary(
                            itemsRestored = itemsRestored,
                            collectionsRestored = collectionsRestored,
                            filesRestored = filesRestored,
                            replaced = mode == RestoreMode.REPLACE
                        )
                    } finally {
                        header.wipe()
                    }
                }
            } finally {
                Crypto.wipe(currentMaster)
                restoreDir.deleteRecursively()
            }
        }
    }

    private fun readHeader(
        input: DataInputStream,
        password: CharArray
    ): Header {
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        if (!magic.contentEquals(MAGIC)) error("This is not a Locker backup")
        val format = input.readInt()
        if (format != FORMAT_VERSION) error("Unsupported backup version")
        val kdfVersion = input.readInt()
        val params = Crypto.paramsFor(kdfVersion) ?: error("Unsupported KDF version")
        val salt = input.readBytesCompat(Crypto.SALT_BYTES) ?: error("Corrupt backup header")
        val wrappedMaster = input.readBytesCompat(4096) ?: error("Corrupt backup header")
        val manifestBlob = input.readBytesCompat(MAX_METADATA) ?: error("Corrupt backup header")
        val prefix = ByteArray(4)
        input.readFully(prefix)
        input.readLong() // creation time; also present in the manifest

        val derived = try {
            Crypto.deriveKey(password, salt, params)
        } finally {
            password.fill('\u0000')
        }
        try {
            val master = Crypto.decrypt(derived, wrappedMaster, AAD_BACKUP_MASTER)
                ?: error("Incorrect backup password")
            val manifestBytes = Crypto.decrypt(derived, manifestBlob, AAD_BACKUP_MANIFEST)
                ?: error("Backup manifest is damaged")
            val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
            return Header(master, derived, manifest, prefix)
        } catch (t: Throwable) {
            Crypto.wipe(derived)
            throw t
        }
    }

    private data class Header(
        val masterKey: ByteArray,
        val backupKey: ByteArray,
        val manifest: JSONObject,
        val prefix: ByteArray
    ) {
        fun wipe() {
            Crypto.wipe(masterKey, backupKey, prefix)
        }
    }

    private data class BackupRecord(val type: Int, val id: String, val size: Long)

    private inner class RecordWriter(
        private val out: DataOutputStream,
        private val key: ByteArray,
        private val prefix: ByteArray
    ) {
        private var counter = 0
        private val digest = MessageDigest.getInstance("SHA-256")

        fun writeBytes(type: Int, id: String, bytes: ByteArray) {
            ByteArrayInputStream(bytes).use { input ->
                write(type, id, input, bytes.size.toLong())
            }
        }

        fun write(type: Int, id: String, input: InputStream, size: Long) {
            if (size < 0) error("Invalid record size")
            writeHeader(type, id, size)
            var remaining = size
            var index = 0
            val buffer = ByteArray(CHUNK_SIZE)
            while (remaining > 0L) {
                val read = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                var offset = 0
                while (offset < read) {
                    val count = input.read(buffer, offset, read - offset)
                    if (count < 0) error("Unexpected end of record")
                    offset += count
                }
                digest.update(buffer, 0, read)
                val ciphertext = Crypto.encryptStreamChunk(
                    key,
                    nonce(prefix, counter, index),
                    buffer,
                    read,
                    recordAad(type, id, counter, index)
                )
                out.writeInt(ciphertext.size)
                out.write(ciphertext)
                remaining -= read
                index++
            }
            counter++
        }

        fun writeEnd() {
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val bytes = hash.toByteArray(Charsets.UTF_8)
            writeHeader(TYPE_END, "", bytes.size.toLong())
            val ciphertext = Crypto.encryptStreamChunk(
                key,
                nonce(prefix, counter, 0),
                bytes,
                bytes.size,
                recordAad(TYPE_END, "", counter, 0)
            )
            out.writeInt(ciphertext.size)
            out.write(ciphertext)
            counter++
        }

        private fun writeHeader(type: Int, id: String, size: Long) {
            val idBytes = id.toByteArray(Charsets.UTF_8)
            require(idBytes.size <= 512) { "Record id too long" }
            out.writeByte(type)
            out.writeShort(idBytes.size)
            out.write(idBytes)
            out.writeLong(size)
        }
    }

    private inner class RecordReader(
        private val input: DataInputStream,
        private val key: ByteArray,
        private val prefix: ByteArray
    ) {
        private var counter = 0
        private val digest = MessageDigest.getInstance("SHA-256")

        private var current: BackupRecord? = null

        fun readHeader(): BackupRecord {
            val type = input.readUnsignedByte()
            val idLength = input.readUnsignedShort()
            if (idLength > 512) error("Corrupt record id")
            val idBytes = ByteArray(idLength)
            input.readFully(idBytes)
            val size = input.readLong()
            if (size < 0 || size > MAX_RECORD_BYTES) error("Corrupt record size")
            return BackupRecord(type, String(idBytes, Charsets.UTF_8), size).also { current = it }
        }

        fun readBytes(maxBytes: Int, trackDigest: Boolean = true): ByteArray {
            val out = ByteArrayOutputStream()
            readCurrent(out, maxBytes.toLong(), trackDigest)
            return out.toByteArray()
        }

        fun readTo(output: OutputStream) {
            readCurrent(output, MAX_RECORD_BYTES, trackDigest = true)
        }

        fun digestHex(): String = digest.digest().joinToString("") { "%02x".format(it) }

        private fun readCurrent(output: OutputStream, limit: Long, trackDigest: Boolean) {
            val record = current ?: error("Record header missing")
            current = null
            var remaining = record.size
            var written = 0L
            var index = 0
            while (remaining > 0L) {
                val ciphertextLength = input.readInt()
                if (ciphertextLength <= 16 || ciphertextLength > CHUNK_SIZE + 16) {
                    error("Corrupt record chunk")
                }
                val ciphertext = ByteArray(ciphertextLength)
                input.readFully(ciphertext)
                val plaintext = Crypto.decryptStreamChunk(
                    key,
                    nonce(prefix, counter, index),
                    ciphertext,
                    recordAad(record.type, record.id, counter, index)
                ) ?: error("Backup record authentication failed")
                if (plaintext.size > remaining) error("Corrupt record length")
                written += plaintext.size
                if (written > limit) error("Backup record is too large")
                if (trackDigest) digest.update(plaintext)
                output.write(plaintext)
                remaining -= plaintext.size
                index++
            }
            counter++
        }
    }

    private fun nonce(prefix: ByteArray, counter: Int, chunk: Int): ByteArray =
        prefix + ByteBuffer.allocate(8).putInt(counter).putInt(chunk).array()

    private fun recordAad(type: Int, id: String, counter: Int, chunk: Int): ByteArray =
        "locker:backup:record:v1:$type:$id:$counter:$chunk".toByteArray()

    private fun encodeItemRow(row: ItemRow): ByteArray = JSONObject()
        .put("type", row.type)
        .put("createdAt", row.createdAt)
        .put("updatedAt", row.updatedAt)
        .put("deletedAt", row.deletedAt ?: JSONObject.NULL)
        .put("payload", Crypto.b64encode(row.payload))
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun decodeItemRow(id: String, bytes: ByteArray): ItemRow {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        return ItemRow(
            id = id,
            type = json.getString("type"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            deletedAt = if (json.isNull("deletedAt")) null else json.getLong("deletedAt"),
            payload = json.getString("payload").let { Crypto.b64decode(it) ?: error("Corrupt payload") }
        )
    }

    private fun encodeCollectionRow(row: NameRow): ByteArray = JSONObject()
        .put("createdAt", row.createdAt)
        .put("payload", Crypto.b64encode(row.payload))
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun decodeCollectionRow(id: String, bytes: ByteArray): NameRow {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        return NameRow(
            id = id,
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("createdAt"),
            payload = json.getString("payload").let { Crypto.b64decode(it) ?: error("Corrupt payload") }
        )
    }

    private fun encodeNameRow(row: NameRow): ByteArray = JSONObject()
        .put("createdAt", row.createdAt)
        .put("updatedAt", row.updatedAt)
        .put("payload", Crypto.b64encode(row.payload))
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun decodeNameRow(id: String, bytes: ByteArray): NameRow {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        return NameRow(
            id = id,
            createdAt = json.getLong("createdAt"),
            updatedAt = json.optLong("updatedAt", json.getLong("createdAt")),
            payload = json.getString("payload").let { Crypto.b64decode(it) ?: error("Corrupt payload") }
        )
    }

    private fun DataOutputStream.writeBytesCompat(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBytesCompat(max: Int): ByteArray? {
        val length = try {
            readInt()
        } catch (_: Exception) {
            return null
        }
        if (length < 0 || length > max) return null
        return ByteArray(length).also { readFully(it) }
    }

    private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "Locker-$stamp.locker"
    }

    companion object {
        private val MAGIC = byteArrayOf(
            'L'.code.toByte(), 'K'.code.toByte(), 'R'.code.toByte(),
            'B'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte(),
            '0'.code.toByte(), '1'.code.toByte()
        )
        private const val FORMAT_VERSION = 1
        private const val CHUNK_SIZE = 128 * 1024
        private const val MAX_METADATA = 16 * 1024 * 1024
        private const val MAX_RECORD_BYTES = 64L * 1024 * 1024 * 1024

        private const val TYPE_ITEM = 1
        private const val TYPE_COLLECTION = 2
        private const val TYPE_LEGACY = 3
        private const val TYPE_PROFILE = 4
        private const val TYPE_FILE = 5
        private const val TYPE_THUMB = 6
        private const val TYPE_END = 7

        private val AAD_BACKUP_MASTER = "locker:backup:master:v1".toByteArray()
        private val AAD_BACKUP_MANIFEST = "locker:backup:manifest:v1".toByteArray()
    }
}
