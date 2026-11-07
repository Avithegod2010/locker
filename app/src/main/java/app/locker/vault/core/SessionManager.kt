package app.locker.vault.core

import app.locker.vault.data.EncryptedFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

enum class SessionState {
    SETUP,
    RECOVERY,
    BIOMETRIC_SETUP,
    LOCKED,
    UNLOCKED
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object DuressUnlocked : UnlockResult
    data object HiddenUnlocked : UnlockResult
    data object WrongCredentials : UnlockResult
    data object InvalidRecoveryKey : UnlockResult
    data class Failure(val message: String) : UnlockResult
}

/**
 * Owns the in-memory master key and the lock lifecycle. The key is zeroed whenever the
 * vault locks; nothing in this class ever writes the master key to disk unwrapped.
 */
class SessionManager(
    private val preferences: AppPreferences,
    private val keyMaterialStore: KeyMaterialStore,
    private val biometricKeyStore: BiometricKeyStore,
    private val fileStore: EncryptedFileStore,
    private val scope: CoroutineScope,
    private val failureScope: String = "main"
) {
    private val _state = MutableStateFlow(
        if (keyMaterialStore.hasVault()) SessionState.LOCKED else SessionState.SETUP
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _pendingRecoveryCode = MutableStateFlow<String?>(null)
    val pendingRecoveryCode: StateFlow<String?> = _pendingRecoveryCode.asStateFlow()

    private var masterKey: ByteArray? = null
    private var lockJob: Job? = null
    private var externalHoldJob: Job? = null
    private var externalHoldCount = 0
    private var appInBackground = false
    private var failedAttempts = preferences.failedAttempts(failureScope)
    private var lockEpoch = 0

    /** Wired by the app container so a freshly created vault gets its starter collections. */
    var onVaultCreated: (suspend () -> Unit)? = null

    /** Wired by the app container for optional self-destruct after repeated failures. */
    var onWipeRequired: (suspend () -> Unit)? = null

    /** Wired by the app container to purge decrypted metadata from memory on lock. */
    var onLocked: (() -> Unit)? = null

    /** Wired by the app container to verify database integrity after a successful unlock. */
    var onUnlocked: (() -> Unit)? = null

    /** Wired for spaces whose lock-screen password verifier must follow a password change. */
    var onPasswordChanged: ((saltBase64: String, verifierBase64: String) -> Unit)? = null

    /** Wired by the app container to destroy every trace of the real vault on duress. */
    var onDuressWipe: (suspend () -> Unit)? = null

    /** Wired by the app container to seed the fresh decoy vault with innocuous content. */
    var onDecoyReady: (suspend () -> Unit)? = null

    /** Kept for a short time after a password unlock so an automatic backup can run. */
    private var unlockPasswordCopy: CharArray? = null

    /**
     * Returns a defensive copy. Long-running writes must never hold the live session array,
     * because [lock] zeroes that array while the write may still be in flight.
     */
    fun requireMasterKey(): ByteArray =
        masterKey?.copyOf() ?: throw IllegalStateException("Vault is locked")

    /** Returns a defensive copy, or null when locked. */
    fun masterKeyOrNull(): ByteArray? = masterKey?.copyOf()

    fun hasVault(): Boolean = keyMaterialStore.hasVault()

    fun pendingRecoveryCode(): String? = _pendingRecoveryCode.value

    /** Returns the numeric recovery code while unlocked, or null if it cannot be read. */
    fun recoveryCode(): String? {
        val key = masterKey?.copyOf() ?: return null
        return try {
            keyMaterialStore.recoveryCodeCopy(key)
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun createVault(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        try {
            if (keyMaterialStore.hasVault()) return@withContext UnlockResult.Failure("A vault already exists")
            if (password.size < 10) {
                return@withContext UnlockResult.Failure("Use at least 10 characters for your vault password")
            }
            val created = keyMaterialStore.createVault(password)
            masterKey = created.masterKey
            _pendingRecoveryCode.value = created.recoveryCode
            // Seeding default collections is a convenience; a failure here must never make
            // the just-created vault unreachable.
            runCatching { onVaultCreated?.invoke() }
            _state.value = SessionState.RECOVERY
            UnlockResult.Success
        } catch (t: Throwable) {
            UnlockResult.Failure(t.message ?: "Could not create the vault")
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val epoch = lockEpoch
        val attempt = failedAttempts + 1
        failedAttempts = attempt
        // commit() so force-stopping the process cannot outrun the attempt counter
        preferences.setFailedAttempts(attempt, failureScope)
        try {
            val unwrapped = keyMaterialStore.unwrapWithPassword(password)
                ?: return@withContext registerFailure(attempt)
            if (lockEpoch != epoch) {
                Crypto.wipe(unwrapped)
                failedAttempts = 0
                preferences.setFailedAttempts(0, failureScope)
                return@withContext UnlockResult.Failure("Vault locked during unlock")
            }
            rememberPasswordForBackup(password)
            completeUnlock(unwrapped, viaRecovery = false)
            UnlockResult.Success
        } catch (t: Throwable) {
            UnlockResult.Failure(t.message ?: "Could not unlock")
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Verifies a password without touching the failed-attempt counter. Used to let the real
     * password open the real vault from the decoy's lock screen without risking its
     * erase-after-N-attempts protection.
     */
    suspend fun tryUnlockQuiet(password: CharArray): Boolean = withContext(Dispatchers.Default) {
        val epoch = lockEpoch
        try {
            val unwrapped = keyMaterialStore.unwrapWithPassword(password) ?: return@withContext false
            if (lockEpoch != epoch) {
                Crypto.wipe(unwrapped)
                return@withContext false
            }
            rememberPasswordForBackup(password)
            completeUnlock(unwrapped, viaRecovery = false)
            true
        } catch (_: Throwable) {
            false
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun unlockWithRecovery(input: String): UnlockResult = withContext(Dispatchers.Default) {        val epoch = lockEpoch
        try {
            val unwrapped = keyMaterialStore.unwrapWithRecovery(input)
                ?: return@withContext UnlockResult.InvalidRecoveryKey
            if (lockEpoch != epoch) {
                Crypto.wipe(unwrapped)
                return@withContext UnlockResult.Failure("Vault locked during unlock")
            }
            preferences.setRecoveryUsed(true)
            completeUnlock(unwrapped, viaRecovery = true)
            UnlockResult.Success
        } catch (t: Throwable) {
            UnlockResult.Failure(t.message ?: "Could not verify recovery key")
        }
    }

    // --- Duress / decoy vault -------------------------------------------------

    fun hasDuressPassword(): Boolean =
        preferences.settings.value.duressVerifier.isNotBlank()

    suspend fun setDuressPassword(password: CharArray): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            runCatching {
                if (masterKey == null) error("Unlock the vault first")
                if (password.size < 10) {
                    error("Use at least 10 characters for the duress password")
                }
                val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
                val key = Crypto.deriveKey(password, salt, Crypto.currentKdfParams)
                val verifier = try {
                    Crypto.sha256(key)
                } finally {
                    Crypto.wipe(key)
                }
                preferences.setDuressCredentials(
                    Crypto.b64encode(salt),
                    Crypto.b64encode(verifier)
                )
                Crypto.wipe(verifier)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    fun clearDuressPassword() = preferences.clearDuressCredentials()

    /** True when this space has a biometric-wrapped payload (main vault only by default). */
    fun hasBiometricPayload(): Boolean = keyMaterialStore.biometricPayload() != null

    /**
     * Fast dispatcher check used by the unlock coordinator to decide whether the entered
     * password should open the decoy space. New verifiers are salted SHA-256 (they protect
     * only the fake vault dispatch, never real data); legacy Argon2 verifiers still match.
     */
    fun matchesDuressPassword(password: CharArray): Boolean {
        val settings = preferences.settings.value
        if (settings.duressVerifier.isBlank() || settings.duressSalt.isBlank()) return false
        val salt = Crypto.b64decode(settings.duressSalt) ?: return false
        val expected = Crypto.b64decode(settings.duressVerifier) ?: return false
        return if (settings.duressFastVerifier) {
            val actual = Crypto.sha256(
                salt + password.concatToString().toByteArray(Charsets.UTF_8)
            )
            try {
                actual.contentEquals(expected)
            } finally {
                Crypto.wipe(actual)
                Crypto.wipe(expected)
            }
        } else {
            try {
                val key = Crypto.deriveKey(password, salt, Crypto.currentKdfParams)
                val actual = try {
                    Crypto.sha256(key)
                } finally {
                    Crypto.wipe(key)
                }
                try {
                    actual.contentEquals(expected)
                } finally {
                    Crypto.wipe(actual)
                    Crypto.wipe(expected)
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Destroys the real vault and immediately creates a fresh decoy vault protected by the
     * same password that was just typed. The decoy never contains any original data.
     */
    private suspend fun performDuressUnlock(password: CharArray): UnlockResult {
        val wipe = onDuressWipe
            ?: return UnlockResult.Failure("Duress reset is unavailable")
        return try {
            wipe()
            val created = keyMaterialStore.createVault(password)
            masterKey?.fill(0)
            masterKey = created.masterKey
            _pendingRecoveryCode.value = null
            failedAttempts = 0
            preferences.setFailedAttempts(0, failureScope)
            preferences.setBiometricEnabled(false)
            preferences.setRecoveryUsed(false)
            _state.value = SessionState.UNLOCKED
            runCatching { onVaultCreated?.invoke() }
            runCatching { onDecoyReady?.invoke() }
            UnlockResult.DuressUnlocked
        } catch (t: Throwable) {
            UnlockResult.Failure(t.message ?: "Duress reset failed")
        } finally {
            password.fill('\u0000')
        }
    }

    private fun rememberPasswordForBackup(password: CharArray) {
        unlockPasswordCopy?.fill('\u0000')
        unlockPasswordCopy = password.copyOf()
        scope.launch {
            delay(5 * 60 * 1000L)
            unlockPasswordCopy?.fill('\u0000')
            unlockPasswordCopy = null
        }
    }

    /**
     * Hands the transient password copy to the automatic backup runner and forgets it.
     * Returns null when no password is available (for example after a biometric unlock).
     */
    fun takeUnlockPasswordForBackup(): CharArray? {
        val copy = unlockPasswordCopy
        unlockPasswordCopy = null
        return copy
    }

    fun prepareBiometricEnrollmentCipher(): Cipher = biometricKeyStore.createEnrollmentCipher()

    fun completeBiometricEnrollment(cipher: Cipher): Boolean {
        val key = masterKey ?: return false
        return try {
            val ciphertext = cipher.doFinal(key)
            keyMaterialStore.storeBiometricPayload(ciphertext, cipher.iv)
            preferences.setBiometricEnabled(true)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun prepareBiometricUnlockCipher(): Cipher? {
        val payload = keyMaterialStore.biometricPayload() ?: return null
        return runCatching {
            biometricKeyStore.createUnlockCipher(payload.iv)
        }.getOrNull()
    }

    fun unlockWithBiometricCipher(cipher: Cipher): UnlockResult {
        val payload = keyMaterialStore.biometricPayload()
            ?: return UnlockResult.Failure("Biometric unlock is not set up")
        return try {
            val master = cipher.doFinal(payload.ciphertext)
            completeUnlock(master, viaRecovery = false)
            UnlockResult.Success
        } catch (_: Exception) {
            disableBiometrics()
            UnlockResult.Failure("Biometric key is no longer valid. Unlock with your password.")
        }
    }

    fun disableBiometrics() {
        keyMaterialStore.clearBiometricPayload()
        biometricKeyStore.deleteKey()
        preferences.setBiometricEnabled(false)
    }

    /** Issues a fresh recovery key. Requires an unlocked vault. */
    fun rotateRecoveryKey(): String? {
        val key = masterKey?.copyOf() ?: return null
        return try {
            keyMaterialStore.rotateRecovery(key)
        } catch (_: Exception) {
            null
        } finally {
            Crypto.wipe(key)
        }
    }

    suspend fun changePassword(newPassword: CharArray): UnlockResult {
        if (newPassword.size < 10) {
            newPassword.fill('\u0000')
            return UnlockResult.Failure("Use at least 10 characters for your new password")
        }
        val key = masterKey?.copyOf() ?: return UnlockResult.Failure("Vault is locked")
        return withContext(Dispatchers.Default) {
            try {
                keyMaterialStore.changePassword(key, newPassword)
                preferences.setRecoveryUsed(false)
                onPasswordChanged?.let { callback ->
                    val salt = Crypto.randomBytes(16)
                    val digest = Crypto.sha256(
                        salt + newPassword.concatToString().toByteArray(Charsets.UTF_8)
                    )
                    try {
                        callback(Crypto.b64encode(salt), Crypto.b64encode(digest))
                    } finally {
                        Crypto.wipe(digest)
                    }
                }
                UnlockResult.Success
            } catch (t: Throwable) {
                UnlockResult.Failure(t.message ?: "Could not change password")
            } finally {
                Crypto.wipe(key)
                newPassword.fill('\u0000')
            }
        }
    }

    fun acknowledgeRecoverySaved() {
        if (_state.value == SessionState.RECOVERY) {
            _state.value = SessionState.BIOMETRIC_SETUP
        }
    }

    fun completeOnboarding() {
        preferences.setOnboardingComplete(true)
        _pendingRecoveryCode.value = null
        if (masterKey != null) {
            _state.value = SessionState.UNLOCKED
        }
    }

    fun lock() {
        if (_state.value == SessionState.SETUP ||
            _state.value == SessionState.RECOVERY ||
            _state.value == SessionState.BIOMETRIC_SETUP
        ) {
            return
        }
        lockJob?.cancel()
        lockJob = null
        externalHoldJob?.cancel()
        externalHoldJob = null
        externalHoldCount = 0
        lockEpoch++
        masterKey?.fill(0)
        masterKey = null
        unlockPasswordCopy?.fill('\u0000')
        unlockPasswordCopy = null
        _pendingRecoveryCode.value = null
        scope.launch(Dispatchers.IO) {
            runCatching { fileStore.clearSensitiveCaches() }
        }
        onLocked?.invoke()
        if (keyMaterialStore.hasVault()) {
            _state.value = SessionState.LOCKED
        }
    }

    fun onAppBackgrounded() {
        appInBackground = true
        if (_state.value != SessionState.UNLOCKED) return
        lockJob?.cancel()
        if (externalHoldCount > 0) {
            // A camera, picker, or share target is in front. Defer auto-lock so its result
            // can still be delivered; a fallback job caps the hold at ten minutes.
            return
        }
        scheduleLock()
    }

    fun onAppForegrounded() {
        appInBackground = false
        lockJob?.cancel()
        lockJob = null
    }

    /**
     * Call before launching the camera, a system picker, or an external viewer/share target.
     * The auto-lock timer is suspended until [endExternalActivity] (or a 10-minute failsafe).
     */
    fun beginExternalActivity() {
        externalHoldCount++
        lockJob?.cancel()
        lockJob = null
        if (externalHoldJob == null) {
            externalHoldJob = scope.launch {
                delay(10 * 60 * 1000L)
                externalHoldCount = 0
                externalHoldJob = null
                if (appInBackground && _state.value == SessionState.UNLOCKED) {
                    lock()
                }
            }
        }
    }

    fun endExternalActivity() {
        externalHoldCount = (externalHoldCount - 1).coerceAtLeast(0)
        if (externalHoldCount == 0) {
            externalHoldJob?.cancel()
            externalHoldJob = null
            if (appInBackground && _state.value == SessionState.UNLOCKED) {
                scheduleLock()
            }
        }
    }

    private fun scheduleLock() {
        if (_state.value != SessionState.UNLOCKED) return
        lockJob?.cancel()
        val timeoutSeconds = preferences.settings.value.autoLockSeconds
        lockJob = scope.launch {
            delay(timeoutSeconds.coerceAtLeast(0) * 1000L)
            if (_state.value == SessionState.UNLOCKED) lock()
        }
    }

    fun resetToSetup() {
        lockEpoch++
        masterKey?.fill(0)
        masterKey = null
        unlockPasswordCopy?.fill('\u0000')
        unlockPasswordCopy = null
        _pendingRecoveryCode.value = null
        failedAttempts = 0
        preferences.setFailedAttempts(0, failureScope)
        onLocked?.invoke()
        _state.value = SessionState.SETUP
    }

    /**
     * Ends a just-created vault session without going through onboarding screens. Used by
     * the decoy space after its starter content has been seeded.
     */
    fun forceLocked() {
        lockEpoch++
        masterKey?.fill(0)
        masterKey = null
        unlockPasswordCopy?.fill('\u0000')
        unlockPasswordCopy = null
        _pendingRecoveryCode.value = null
        failedAttempts = 0
        preferences.setFailedAttempts(0, failureScope)
        onLocked?.invoke()
        _state.value = if (keyMaterialStore.hasVault()) {
            SessionState.LOCKED
        } else {
            SessionState.SETUP
        }
    }

    private fun completeUnlock(key: ByteArray, viaRecovery: Boolean) {
        masterKey?.fill(0)
        masterKey = key
        failedAttempts = 0
        preferences.setFailedAttempts(0, failureScope)
        _state.value = SessionState.UNLOCKED
        onUnlocked?.invoke()
    }

    private suspend fun registerFailure(attempt: Int): UnlockResult {
        val threshold = preferences.settings.value.wipeAfterAttempts
        if (threshold > 0 && attempt >= threshold) {
            onWipeRequired?.invoke()
            return UnlockResult.Failure("Vault erased after $threshold failed attempts")
        }
        // Gentle progressive backoff; the committed counter is the real defense.
        delay((attempt.coerceAtMost(5) * 250L))
        return UnlockResult.WrongCredentials
    }
}

