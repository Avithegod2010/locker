package app.locker.vault.core

import app.locker.vault.data.VaultDb
import java.io.File
import java.security.MessageDigest

/**
 * Best-effort tamper evidence for the encrypted database. Locker records a fingerprint after
 * a clean lock and compares it on the next unlock. Replacing or rolling back vault.db makes
 * the fingerprint differ and raises a warning. A root attacker who can also edit app-private
 * preferences can hide the change; this is detection, not cryptographic prevention.
 */
class TamperGuard(context: android.content.Context, databaseName: String) {
    private val prefs = context.getSharedPreferences(
        "locker_integrity_$databaseName",
        android.content.Context.MODE_PRIVATE
    )

    fun record(db: VaultDb) {
        db.checkpoint()
        val file = db.databaseFile()
        if (!file.exists()) return
        val hash = hash(file) ?: return
        prefs.edit()
            .putString(KEY_HASH, hash)
            .putLong(KEY_SIZE, file.length())
            .putLong(KEY_RECORDED_AT, System.currentTimeMillis())
            .putBoolean(KEY_CLEAN, true)
            .apply()
    }

    /**
     * Returns true when the database matches the last fingerprint, or when the previous
     * session did not end in a clean lock (a force-stop must not look like tampering).
     */
    fun verify(db: VaultDb): Boolean {
        val wasClean = prefs.getBoolean(KEY_CLEAN, false)
        if (!wasClean) {
            record(db)
            prefs.edit().putBoolean(KEY_CLEAN, false).apply()
            return true
        }
        db.checkpoint()
        val file = db.databaseFile()
        if (!file.exists()) {
            prefs.edit().putBoolean(KEY_CLEAN, false).apply()
            return true
        }
        val expected = prefs.getString(KEY_HASH, null)
        if (expected.isNullOrBlank()) {
            record(db)
            prefs.edit().putBoolean(KEY_CLEAN, false).apply()
            return true
        }
        val actual = hash(file) ?: return false
        prefs.edit().putBoolean(KEY_CLEAN, false).apply()
        return actual == expected
    }

    private fun hash(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private companion object {
        const val KEY_HASH = "db_sha256"
        const val KEY_SIZE = "db_size"
        const val KEY_RECORDED_AT = "recorded_at"
        const val KEY_CLEAN = "clean_lock"
    }
}
