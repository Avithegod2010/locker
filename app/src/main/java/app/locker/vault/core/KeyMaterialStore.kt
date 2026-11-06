package app.locker.vault.core

import android.content.Context

data class CreatedVault(
    val masterKey: ByteArray,
    val recoveryCode: String
)

data class BiometricPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray
)

/**
 * Persists only *wrapped* copies of the vault master key. Nothing here can be decrypted
 * without the user's password, recovery key, or an authenticated biometric Keystore key.
 */
class KeyMaterialStore(context: Context) {
    private val prefs = context.getSharedPreferences("locker_key_material", Context.MODE_PRIVATE)

    fun hasVault(): Boolean = prefs.contains(KEY_WRAPPED_MASTER)

    fun createVault(password: CharArray): CreatedVault {
        val masterKey = Crypto.randomBytes(Crypto.MASTER_KEY_BYTES)
        val passwordSalt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val passwordKey = Crypto.deriveKey(password, passwordSalt)
        val passwordWrapped = Crypto.encrypt(passwordKey, masterKey, AAD_PASSWORD)
        Crypto.wipe(passwordKey)

        val (recoverySecret, recoveryCode) = Base32.createRecoverySecret()
        val recoverySalt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val recoveryKey = Crypto.deriveKey(recoverySecret, recoverySalt)
        val recoveryWrapped = Crypto.encrypt(recoveryKey, masterKey, AAD_RECOVERY)
        Crypto.wipe(recoveryKey, recoverySecret)

        prefs.edit()
            .putInt(KEY_KDF_VERSION, Crypto.kdfVersion)
            .putString(KEY_PASSWORD_SALT, Crypto.b64encode(passwordSalt))
            .putString(KEY_WRAPPED_MASTER, Crypto.b64encode(passwordWrapped))
            .putInt(KEY_RECOVERY_KDF_VERSION, Crypto.kdfVersion)
            .putString(KEY_RECOVERY_SALT, Crypto.b64encode(recoverySalt))
            .putString(KEY_RECOVERY_WRAPPED, Crypto.b64encode(recoveryWrapped))
            .putString(
                KEY_RECOVERY_CODE_COPY,
                Crypto.b64encode(
                    Crypto.encrypt(
                        masterKey,
                        recoveryCode.toByteArray(Charsets.UTF_8),
                        AAD_RECOVERY_COPY
                    )
                )
            )
            .apply()

        return CreatedVault(masterKey, recoveryCode)
    }

    /**
     * The recovery code itself is kept encrypted under the vault master key so an unlocked user
     * can retrieve it later (e.g. after a process death during onboarding). It is never written
     * in the clear and is removed by [wipe].
     */
    fun recoveryCodeCopy(masterKey: ByteArray): String? {
        val wrapped = prefs.getString(KEY_RECOVERY_CODE_COPY, null)?.let(Crypto::b64decode) ?: return null
        val plaintext = Crypto.decrypt(masterKey, wrapped, AAD_RECOVERY_COPY) ?: return null
        return String(plaintext, Charsets.UTF_8)
    }

    fun unwrapWithPassword(password: CharArray): ByteArray? {
        val salt = prefs.getString(KEY_PASSWORD_SALT, null)?.let(Crypto::b64decode) ?: return null
        val wrapped = prefs.getString(KEY_WRAPPED_MASTER, null)?.let(Crypto::b64decode) ?: return null
        val version = prefs.getInt(KEY_KDF_VERSION, 1)
        val params = Crypto.paramsFor(version) ?: return null
        val key = Crypto.deriveKey(password, salt, params)
        val master = Crypto.decrypt(key, wrapped, AAD_PASSWORD)
        Crypto.wipe(key)
        if (master != null && version != Crypto.kdfVersion) {
            // Transparently migrate legacy KDF parameters after a successful unlock.
            runCatching { persistPasswordWrap(master, password) }
        }
        return master
    }

    fun unwrapWithRecovery(input: String): ByteArray? {
        val secret = Base32.parseRecoverySecret(input) ?: return null
        val salt = prefs.getString(KEY_RECOVERY_SALT, null)?.let(Crypto::b64decode) ?: return null
        val wrapped = prefs.getString(KEY_RECOVERY_WRAPPED, null)?.let(Crypto::b64decode) ?: return null
        val version = prefs.getInt(KEY_RECOVERY_KDF_VERSION, 1)
        val params = Crypto.paramsFor(version) ?: return null
        val key = Crypto.deriveKey(secret, salt, params)
        val master = Crypto.decrypt(key, wrapped, AAD_RECOVERY)
        Crypto.wipe(key)
        if (master != null && version != Crypto.kdfVersion) {
            runCatching { persistRecoveryWrap(master, secret) }
        }
        Crypto.wipe(secret)
        return master
    }

    /** Re-wraps the same master key under a new password. The recovery key stays valid. */
    fun changePassword(masterKey: ByteArray, newPassword: CharArray) {
        persistPasswordWrap(masterKey, newPassword)
    }

    /**
     * Issues a fresh recovery key. The old written key stops working immediately.
     */
    fun rotateRecovery(masterKey: ByteArray): String {
        val (secret, code) = Base32.createRecoverySecret()
        try {
            persistRecoveryWrap(masterKey, secret)
            prefs.edit()
                .putString(
                    KEY_RECOVERY_CODE_COPY,
                    Crypto.b64encode(
                        Crypto.encrypt(masterKey, code.toByteArray(Charsets.UTF_8), AAD_RECOVERY_COPY)
                    )
                )
                .apply()
            return code
        } finally {
            Crypto.wipe(secret)
        }
    }

    private fun persistPasswordWrap(masterKey: ByteArray, password: CharArray) {
        val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val key = Crypto.deriveKey(password, salt, Crypto.currentKdfParams)
        val wrapped = Crypto.encrypt(key, masterKey, AAD_PASSWORD)
        Crypto.wipe(key)
        prefs.edit()
            .putInt(KEY_KDF_VERSION, Crypto.kdfVersion)
            .putString(KEY_PASSWORD_SALT, Crypto.b64encode(salt))
            .putString(KEY_WRAPPED_MASTER, Crypto.b64encode(wrapped))
            .apply()
    }

    private fun persistRecoveryWrap(masterKey: ByteArray, secret: ByteArray) {
        val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val key = Crypto.deriveKey(secret, salt, Crypto.currentKdfParams)
        val wrapped = Crypto.encrypt(key, masterKey, AAD_RECOVERY)
        Crypto.wipe(key)
        prefs.edit()
            .putInt(KEY_RECOVERY_KDF_VERSION, Crypto.kdfVersion)
            .putString(KEY_RECOVERY_SALT, Crypto.b64encode(salt))
            .putString(KEY_RECOVERY_WRAPPED, Crypto.b64encode(wrapped))
            .apply()
    }

    fun verifyPassword(password: CharArray, masterKey: ByteArray): Boolean {
        val candidate = unwrapWithPassword(password) ?: return false
        val equal = Crypto.isEqual(candidate, masterKey)
        Crypto.wipe(candidate)
        return equal
    }

    fun storeBiometricPayload(ciphertext: ByteArray, iv: ByteArray) {
        prefs.edit()
            .putString(KEY_BIOMETRIC_WRAPPED, Crypto.b64encode(ciphertext))
            .putString(KEY_BIOMETRIC_IV, Crypto.b64encode(iv))
            .apply()
    }

    fun biometricPayload(): BiometricPayload? {
        val ciphertext = prefs.getString(KEY_BIOMETRIC_WRAPPED, null)?.let(Crypto::b64decode) ?: return null
        val iv = prefs.getString(KEY_BIOMETRIC_IV, null)?.let(Crypto::b64decode) ?: return null
        return BiometricPayload(ciphertext, iv)
    }

    fun clearBiometricPayload() {
        prefs.edit()
            .remove(KEY_BIOMETRIC_WRAPPED)
            .remove(KEY_BIOMETRIC_IV)
            .apply()
    }

    fun wipe() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_KDF_VERSION = "kdf_version"
        const val KEY_PASSWORD_SALT = "password_salt"
        const val KEY_WRAPPED_MASTER = "wrapped_master"
        const val KEY_RECOVERY_KDF_VERSION = "recovery_kdf_version"
        const val KEY_RECOVERY_SALT = "recovery_salt"
        const val KEY_RECOVERY_WRAPPED = "wrapped_master_recovery"
        const val KEY_BIOMETRIC_WRAPPED = "wrapped_master_biometric"
        const val KEY_BIOMETRIC_IV = "biometric_iv"
        const val KEY_RECOVERY_CODE_COPY = "recovery_code_copy"

        val AAD_PASSWORD = "locker:master:password:v1".toByteArray()
        val AAD_RECOVERY = "locker:master:recovery:v1".toByteArray()
        val AAD_RECOVERY_COPY = "locker:recovery:display-copy:v1".toByteArray()
    }
}
