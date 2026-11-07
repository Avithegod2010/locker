package app.locker.vault.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ItemRow(
    val id: String,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val payload: ByteArray
)

data class NameRow(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payload: ByteArray
)

/**
 * Deliberately minimal metadata database. Every user-visible value lives inside an
 * AES-256-GCM encrypted payload; only opaque ids, item types, and timestamps are stored
 * in the clear. The item type is needed to choose the right integrity-bound AAD before
 * decryption and is therefore visible in a raw database image.
 */
class VaultDb(
    context: Context,
    private val databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(
    context.applicationContext,
    databaseName,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                payload BLOB NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_items_deleted_at ON items(deleted_at)")
        db.execSQL(
            """
            CREATE TABLE collections (
                id TEXT PRIMARY KEY NOT NULL,
                created_at INTEGER NOT NULL,
                payload BLOB NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE legacy_contacts (
                id TEXT PRIMARY KEY NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                payload BLOB NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE profile (
                id TEXT PRIMARY KEY NOT NULL,
                payload BLOB NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations must preserve existing encrypted payloads.
    }

    fun readItems(): List<ItemRow> {
        val result = mutableListOf<ItemRow>()
        readableDatabase.rawQuery(
            "SELECT id, type, created_at, updated_at, deleted_at, payload FROM items",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ItemRow(
                    id = cursor.getString(0),
                    type = cursor.getString(1),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3),
                    deletedAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                    payload = cursor.getBlob(5)
                )
            }
        }
        return result
    }

    fun readCollections(): List<NameRow> {
        val result = mutableListOf<NameRow>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, payload FROM collections",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += NameRow(
                    id = cursor.getString(0),
                    createdAt = cursor.getLong(1),
                    updatedAt = cursor.getLong(1),
                    payload = cursor.getBlob(2)
                )
            }
        }
        return result
    }

    fun readLegacyContacts(): List<NameRow> {
        val result = mutableListOf<NameRow>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, updated_at, payload FROM legacy_contacts",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += NameRow(
                    id = cursor.getString(0),
                    createdAt = cursor.getLong(1),
                    updatedAt = cursor.getLong(2),
                    payload = cursor.getBlob(3)
                )
            }
        }
        return result
    }

    fun upsertItem(
        id: String,
        type: String,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
        payload: ByteArray
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("type", type)
            put("created_at", createdAt)
            put("updated_at", updatedAt)
            if (deletedAt == null) putNull("deleted_at") else put("deleted_at", deletedAt)
            put("payload", payload)
        }
        writableDatabase.insertWithOnConflict(
            "items", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun setItemDeleted(id: String, deletedAt: Long?) {
        val values = ContentValues().apply {
            if (deletedAt == null) putNull("deleted_at") else put("deleted_at", deletedAt)
        }
        writableDatabase.update("items", values, "id = ?", arrayOf(id))
    }

    fun insertItemRow(row: ItemRow) {
        val values = ContentValues().apply {
            put("id", row.id)
            put("type", row.type)
            put("created_at", row.createdAt)
            put("updated_at", row.updatedAt)
            if (row.deletedAt == null) putNull("deleted_at") else put("deleted_at", row.deletedAt)
            put("payload", row.payload)
        }
        writableDatabase.insertWithOnConflict(
            "items", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun insertCollectionRow(row: NameRow) {
        val values = ContentValues().apply {
            put("id", row.id)
            put("created_at", row.createdAt)
            put("payload", row.payload)
        }
        writableDatabase.insertWithOnConflict(
            "collections", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun insertLegacyRow(row: NameRow) {
        val values = ContentValues().apply {
            put("id", row.id)
            put("created_at", row.createdAt)
            put("updated_at", row.updatedAt)
            put("payload", row.payload)
        }
        writableDatabase.insertWithOnConflict(
            "legacy_contacts", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearAll() {
        writableDatabase.delete("items", null, null)
        writableDatabase.delete("collections", null, null)
        writableDatabase.delete("legacy_contacts", null, null)
        writableDatabase.delete("profile", null, null)
    }

    fun deleteItem(id: String) {
        writableDatabase.delete("items", "id = ?", arrayOf(id))
    }

    fun insertCollection(id: String, createdAt: Long, payload: ByteArray) {
        val values = ContentValues().apply {
            put("id", id)
            put("created_at", createdAt)
            put("payload", payload)
        }
        writableDatabase.insertWithOnConflict(
            "collections", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteCollection(id: String) {
        writableDatabase.delete("collections", "id = ?", arrayOf(id))
    }

    fun upsertLegacyContact(
        id: String,
        createdAt: Long,
        updatedAt: Long,
        payload: ByteArray
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("created_at", createdAt)
            put("updated_at", updatedAt)
            put("payload", payload)
        }
        writableDatabase.insertWithOnConflict(
            "legacy_contacts", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteLegacyContact(id: String) {
        writableDatabase.delete("legacy_contacts", "id = ?", arrayOf(id))
    }

    fun readProfile(): ByteArray? {
        readableDatabase.rawQuery("SELECT payload FROM profile WHERE id = 'me'", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getBlob(0) else null
        }
    }

    fun saveProfile(payload: ByteArray) {
        val values = ContentValues().apply {
            put("id", "me")
            put("payload", payload)
        }
        writableDatabase.insertWithOnConflict(
            "profile", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun wipeAndClose() {
        close()
        appContext.deleteDatabase(databaseName)
    }

    private val appContext: Context = context.applicationContext

    companion object {
        const val DATABASE_NAME = "vault.db"
        private const val DATABASE_VERSION = 1
    }
}
