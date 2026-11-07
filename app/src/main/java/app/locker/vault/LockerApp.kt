package app.locker.vault

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.locker.vault.core.AppPreferences
import app.locker.vault.core.AutoBackupRunner
import app.locker.vault.core.BiometricKeyStore
import app.locker.vault.core.Crypto
import app.locker.vault.core.CrashReporter
import app.locker.vault.core.DisguiseManager
import app.locker.vault.core.KeyMaterialStore
import app.locker.vault.core.PlaybackMemory
import app.locker.vault.core.SecureClipboard
import app.locker.vault.core.SessionManager
import app.locker.vault.core.SessionState
import app.locker.vault.core.TamperGuard
import app.locker.vault.core.UnlockResult
import app.locker.vault.data.BackupManager
import app.locker.vault.data.EncryptedFileStore
import app.locker.vault.data.VaultDb
import app.locker.vault.data.VaultRepository
import app.locker.vault.data.VaultType
import app.locker.vault.ui.components.ThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One independent vault: its own wrapped keys, database, and encrypted files. The main
 * space is the user's real vault; the decoy space is a separate, harmless vault opened by
 * the duress password. Neither can decrypt the other.
 */
class VaultSpace(
    context: Context,
    val id: String,
    private val preferences: AppPreferences,
    val biometricKeyStore: BiometricKeyStore,
    scope: CoroutineScope,
    private val secureClipboard: SecureClipboard,
    prefsName: String,
    dbName: String,
    dirName: String
) {
    private val appContext = context.applicationContext
    val keyMaterialStore = KeyMaterialStore(appContext, prefsName)
    val fileStore = EncryptedFileStore(appContext, dirName)
    val database = VaultDb(appContext, dbName)
    private val tamperGuard = TamperGuard(appContext, dbName)
    val session = SessionManager(
        preferences = preferences,
        keyMaterialStore = keyMaterialStore,
        biometricKeyStore = biometricKeyStore,
        fileStore = fileStore,
        scope = scope,
        failureScope = id
    )
    val repository = VaultRepository(
        context = appContext,
        db = database,
        files = fileStore,
        session = session,
        preferences = preferences
    )
    val backupManager = BackupManager(
        context = appContext,
        db = database,
        files = fileStore,
        repository = repository,
        session = session
    )
    val autoBackup = AutoBackupRunner(
        context = appContext,
        preferences = preferences,
        backupManager = backupManager,
        session = session
    )

    init {
        session.onVaultCreated = { repository.seedDefaults() }
        session.onLocked = {
            repository.clearInMemory()
            ThumbnailCache.clear()
            secureClipboard.clear()
            scope.launch(Dispatchers.IO) {
                runCatching { tamperGuard.record(database) }
            }
        }
        session.onUnlocked = {
            scope.launch(Dispatchers.IO) {
                val intact = runCatching { tamperGuard.verify(database) }.getOrDefault(true)
                if (!intact) preferences.setTamperDetected(true)
            }
        }
    }

    suspend fun wipeData() {
        repository.wipeAllData()
        keyMaterialStore.wipe()
        fileStore.wipeAll()
        session.resetToSetup()
    }

    suspend fun wipeContentsForSetup() {
        repository.wipeAllData()
        keyMaterialStore.wipe()
        fileStore.wipeAll()
    }

    fun lockIfUnlocked() {
        if (session.state.value == SessionState.UNLOCKED) session.lock()
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val preferences = AppPreferences(appContext)
    val biometricKeyStore = BiometricKeyStore(appContext)
    val secureClipboard = SecureClipboard(appContext, appScope)
    val playback = PlaybackMemory(appContext)

    /** Content sent to Locker through the Android share sheet, waiting for the UI. */
    val pendingShare = MutableStateFlow<app.locker.vault.core.SharedContent?>(null)

    /** URIs handed from the share sheet to the multi-file import screen. */
    val pendingImport = MutableStateFlow<List<android.net.Uri>>(emptyList())

    /** A shortcut route to open once the vault is unlocked. */
    val pendingRoute = MutableStateFlow<String?>(null)

    /** A search query handed to the home screen (smart folders). */
    val pendingSearch = MutableStateFlow<String?>(null)

    val mainSpace: VaultSpace = createSpace(
        MAIN_ID,
        "locker_key_material",
        "vault.db",
        "vault"
    )
    private var decoySpace: VaultSpace? = null
    private var hiddenSpace: VaultSpace? = null

    private val _activeSpace = MutableStateFlow(mainSpace)
    val activeSpace: StateFlow<VaultSpace> = _activeSpace.asStateFlow()
    val isDecoyActive: Boolean get() = _activeSpace.value.id == DECOY_ID
    val isHiddenActive: Boolean get() = _activeSpace.value.id == HIDDEN_ID

    // The UI reads these delegates, so every screen follows the active space.
    val session: SessionManager get() = _activeSpace.value.session
    val keyMaterialStore: KeyMaterialStore get() = _activeSpace.value.keyMaterialStore
    val fileStore: EncryptedFileStore get() = _activeSpace.value.fileStore
    val database: VaultDb get() = _activeSpace.value.database
    val repository: VaultRepository get() = _activeSpace.value.repository
    val backupManager: BackupManager get() = _activeSpace.value.backupManager
    val autoBackup: AutoBackupRunner get() = _activeSpace.value.autoBackup

    init {
        mainSpace.session.onWipeRequired = { wipeVault() }
    }

    private fun createSpace(
        id: String,
        prefsName: String,
        dbName: String,
        dirName: String
    ): VaultSpace {
        val space = VaultSpace(
            context = appContext,
            id = id,
            preferences = preferences,
            biometricKeyStore = biometricKeyStore,
            scope = appScope,
            secureClipboard = secureClipboard,
            prefsName = prefsName,
            dbName = dbName,
            dirName = dirName
        )
        if (id == DECOY_ID) {
            space.session.onWipeRequired = {
                space.wipeData()
                if (isDecoyActive) {
                    _activeSpace.value = mainSpace
                }
            }
        }
        if (id == HIDDEN_ID) {
            space.session.onWipeRequired = {
                space.wipeData()
                preferences.clearHiddenCredentials()
                if (isHiddenActive) {
                    _activeSpace.value = mainSpace
                }
            }
        }
        return space
    }

    private fun ensureDecoySpace(): VaultSpace =
        decoySpace ?: createSpace(
            DECOY_ID,
            "locker_key_material_decoy",
            "vault_decoy.db",
            "vault_decoy"
        ).also { decoySpace = it }

    private fun ensureHiddenSpace(): VaultSpace =
        hiddenSpace ?: createSpace(
            HIDDEN_ID,
            "locker_key_material_hidden",
            "vault_hidden.db",
            "vault_hidden"
        ).apply {
            session.onPasswordChanged = { salt, verifier ->
                preferences.setHiddenCredentials(salt, verifier)
                preferences.setHiddenFastVerifier(true)
            }
        }.also { hiddenSpace = it }

    /**
     * Tries the real vault first, then the independent decoy vault. The decoy can also be
     * opened when its in-app password was changed away from the configured duress password.
     */
    suspend fun unlockWithPassword(password: CharArray): UnlockResult =
        withContext(Dispatchers.Default) {
            val main = mainSpace
            val duressConfigured = preferences.settings.value.duressVerifier.isNotBlank()
            val hiddenConfigured = preferences.settings.value.hiddenVerifier.isNotBlank()
            val decoy = if (duressConfigured || decoySpace != null) ensureDecoySpace() else null
            val hidden = if (hiddenConfigured || hiddenSpace != null) ensureHiddenSpace() else null

            if (!isDecoyActive && !isHiddenActive) {
                if (duressConfigured &&
                    main.session.matchesDuressPassword(password.copyOf()) &&
                    decoy != null
                ) {
                    if (!decoy.keyMaterialStore.hasVault()) {
                        createDecoyVault(decoy, password.copyOf())
                    }
                    val decoyAttempt = decoy.session.unlockWithPassword(password.copyOf())
                    if (decoyAttempt is UnlockResult.Success) {
                        switchTo(decoy)
                        password.fill('\u0000')
                        return@withContext UnlockResult.DuressUnlocked
                    }
                }

                if (hiddenConfigured &&
                    matchesHiddenPassword(password) &&
                    hidden != null
                ) {
                    if (!hidden.keyMaterialStore.hasVault()) {
                        createHiddenVault(hidden, password.copyOf())
                    }
                    val hiddenAttempt = hidden.session.unlockWithPassword(password.copyOf())
                    password.fill('\u0000')
                    return@withContext when (hiddenAttempt) {
                        is UnlockResult.Success -> {
                            switchTo(hidden)
                            UnlockResult.HiddenUnlocked
                        }
                        is UnlockResult.Failure -> hiddenAttempt
                        else -> UnlockResult.WrongCredentials
                    }
                }
            }

            if (isDecoyActive) {
                // Let the real password open the real vault without touching its failure
                // counter; wrong guesses are counted against the throwaway decoy only.
                if (main.session.tryUnlockQuiet(password.copyOf())) {
                    switchTo(main)
                    password.fill('\u0000')
                    return@withContext UnlockResult.Success
                }
                val decoyAttempt = decoy?.session?.unlockWithPassword(password.copyOf())
                password.fill('\u0000')
                return@withContext when (decoyAttempt) {
                    is UnlockResult.Failure -> decoyAttempt
                    else -> UnlockResult.WrongCredentials
                }
            }

            if (isHiddenActive) {
                if (main.session.tryUnlockQuiet(password.copyOf())) {
                    switchTo(main)
                    password.fill('\u0000')
                    return@withContext UnlockResult.Success
                }
                val hiddenAttempt = hidden?.session?.unlockWithPassword(password.copyOf())
                password.fill('\u0000')
                return@withContext when (hiddenAttempt) {
                    is UnlockResult.Failure -> hiddenAttempt
                    else -> UnlockResult.WrongCredentials
                }
            }

            val mainAttempt = main.session.unlockWithPassword(password.copyOf())
            if (mainAttempt is UnlockResult.Success) {
                switchTo(main)
                password.fill('\u0000')
                return@withContext mainAttempt
            }

            if (decoy != null && decoy.keyMaterialStore.hasVault()) {
                // A decoy password changed inside the decoy vault still opens it.
                val decoyAttempt = decoy.session.unlockWithPassword(password.copyOf())
                if (decoyAttempt is UnlockResult.Success) {
                    switchTo(decoy)
                    password.fill('\u0000')
                    return@withContext UnlockResult.DuressUnlocked
                }
            }

            password.fill('\u0000')
            mainAttempt
        }

    private fun matchesHiddenPassword(password: CharArray): Boolean {
        val settings = preferences.settings.value
        if (settings.hiddenVerifier.isBlank() || settings.hiddenSalt.isBlank()) return false
        val salt = Crypto.b64decode(settings.hiddenSalt) ?: return false
        val expected = Crypto.b64decode(settings.hiddenVerifier) ?: return false
        return try {
            if (settings.hiddenFastVerifier) {
                val actual = Crypto.sha256(
                    salt + password.concatToString().toByteArray(Charsets.UTF_8)
                )
                try {
                    actual.contentEquals(expected)
                } finally {
                    Crypto.wipe(actual)
                }
            } else {
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
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            Crypto.wipe(expected)
        }
    }

    private fun switchTo(space: VaultSpace) {
        listOfNotNull(mainSpace, decoySpace, hiddenSpace).forEach { candidate ->
            if (candidate !== space) candidate.lockIfUnlocked()
        }
        _activeSpace.value = space
    }

    private suspend fun createDecoyVault(space: VaultSpace, password: CharArray): Boolean =
        runCatching {
            space.wipeContentsForSetup()
            val created = space.session.createVault(password)
            if (created !is UnlockResult.Success) error("Could not create the decoy vault")
            seedDecoyVault(space.repository)
            space.repository.clearInMemory()
            space.session.forceLocked()
            true
        }.getOrDefault(false).also { password.fill('\u0000') }

    /** Creates an empty hidden vault; the user fills it while it is the active space. */
    private suspend fun createHiddenVault(space: VaultSpace, password: CharArray): Boolean =
        runCatching {
            space.wipeContentsForSetup()
            val created = space.session.createVault(password)
            if (created !is UnlockResult.Success) error("Could not create the hidden vault")
            space.repository.clearInMemory()
            space.session.forceLocked()
            true
        }.getOrDefault(false).also { password.fill('\u0000') }

    /** Configures (or rotates) the duress password and rebuilds the decoy vault. */
    suspend fun setDuressPassword(password: CharArray): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                require(!isDecoyActive) { "Switch to the main vault first" }
                require(mainSpace.session.masterKeyOrNull() != null) { "Unlock the vault first" }
                if (password.size < 10) {
                    error("Use at least 10 characters for the duress password")
                }
                val master = mainSpace.session.masterKeyOrNull()
                    ?: error("Unlock the vault first")
                val candidate = password.copyOf()
                val sameAsVault = try {
                    mainSpace.keyMaterialStore.verifyPassword(candidate, master)
                } finally {
                    Crypto.wipe(master)
                    candidate.fill('\u0000')
                }
                if (sameAsVault) {
                    error("Choose a duress password that differs from your vault password")
                }
                val decoy = ensureDecoySpace()
                if (!createDecoyVault(decoy, password.copyOf())) {
                    error("Could not create the decoy vault")
                }
                val salt = Crypto.randomBytes(16)
                val digest = Crypto.sha256(
                    salt + password.concatToString().toByteArray(Charsets.UTF_8)
                )
                preferences.setDuressCredentials(
                    Crypto.b64encode(salt),
                    Crypto.b64encode(digest)
                )
                preferences.setDuressFastVerifier(true)
                Crypto.wipe(digest)
            }.also { password.fill('\u0000') }
        }

    suspend fun clearDuressPassword(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(!isDecoyActive) { "Switch to the main vault first" }
            decoySpace?.wipeData()
            decoySpace = null
            preferences.clearDuressCredentials()
        }
    }

    fun hasHiddenPassword(): Boolean =
        preferences.settings.value.hiddenVerifier.isNotBlank()

    /** Creates or replaces a separate hidden vault, unlocked by its own password. */
    suspend fun setHiddenPassword(password: CharArray): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                require(!isDecoyActive && !isHiddenActive) { "Switch to the main vault first" }
                require(mainSpace.session.masterKeyOrNull() != null) { "Unlock the vault first" }
                if (password.size < 10) {
                    error("Use at least 10 characters for the hidden password")
                }
                val master = mainSpace.session.masterKeyOrNull()
                    ?: error("Unlock the vault first")
                val sameAsVault = try {
                    mainSpace.keyMaterialStore.verifyPassword(password, master)
                } finally {
                    Crypto.wipe(master)
                }
                if (sameAsVault) {
                    error("Choose a hidden password that differs from your vault password")
                }
                if (mainSpace.session.matchesDuressPassword(password.copyOf())) {
                    error("Choose a hidden password that differs from the duress password")
                }
                val hidden = ensureHiddenSpace()
                if (!createHiddenVault(hidden, password.copyOf())) {
                    error("Could not create the hidden vault")
                }
                val salt = Crypto.randomBytes(16)
                val digest = Crypto.sha256(
                    salt + password.concatToString().toByteArray(Charsets.UTF_8)
                )
                preferences.setHiddenCredentials(
                    Crypto.b64encode(salt),
                    Crypto.b64encode(digest)
                )
                preferences.setHiddenFastVerifier(true)
                Crypto.wipe(digest)
            }.also { password.fill('\u0000') }
        }

    suspend fun clearHiddenPassword(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(!isDecoyActive && !isHiddenActive) { "Switch to the main vault first" }
            hiddenSpace?.wipeData()
            hiddenSpace = null
            preferences.clearHiddenCredentials()
        }
    }

    /** Full wipe: both vaults, biometric key, preferences, and in-memory state. */
    suspend fun wipeVault() {
        mainSpace.wipeData()
        decoySpace?.wipeData()
        hiddenSpace?.wipeData()
        decoySpace = null
        hiddenSpace = null
        biometricKeyStore.deleteKey()
        preferences.reset()
        playback.clearAll()
        _activeSpace.value = mainSpace
    }

    fun clearSensitiveCaches() {
        runCatching { mainSpace.fileStore.clearSensitiveCaches() }
        runCatching { decoySpace?.fileStore?.clearSensitiveCaches() }
        runCatching { hiddenSpace?.fileStore?.clearSensitiveCaches() }
    }

    /** Harmless, lived-in starter content for a freshly created decoy vault. */
    private suspend fun seedDecoyVault(repository: VaultRepository) {
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        repository.createTextItem(
            type = VaultType.NOTE,
            title = "Grocery list",
            body = "Milk\nEggs\nBread\nCoffee beans\nLaundry detergent",
            createdAt = now - 2 * day,
            updatedAt = now - 2 * day
        )
        repository.createTextItem(
            type = VaultType.NOTE,
            title = "Warranty receipts",
            body = "Laptop — bought last spring, 2-year cover\nHeadphones — 1-year cover\nKeep receipts in the desk drawer.",
            createdAt = now - 150 * day,
            updatedAt = now - 140 * day
        )
        repository.createTextItem(
            type = VaultType.SECRET,
            title = "Home Wi-Fi",
            username = "home",
            password = "sunflower-2021",
            website = "192.168.1.1",
            createdAt = now - 240 * day,
            updatedAt = now - 240 * day
        )
        repository.createTextItem(
            type = VaultType.SECRET,
            title = "Streaming subscription",
            username = "family@example.com",
            password = "MovieNight!24",
            website = "https://stream.example.com",
            createdAt = now - 95 * day,
            updatedAt = now - 95 * day
        )
        repository.createTextItem(
            type = VaultType.THING,
            title = "Gym membership",
            location = "Wallet",
            body = "Renews in March. Locker key tag is in the front pocket.",
            fields = listOf(
                app.locker.vault.data.CustomField("Member number", "G-20418"),
                app.locker.vault.data.CustomField("Renews", "March")
            ),
            createdAt = now - 180 * day,
            updatedAt = now - 180 * day
        )
        repository.createTextItem(
            type = VaultType.THING,
            title = "Passport",
            location = "Bedroom safe",
            body = "Keep the physical document in the safe with the travel folder.",
            fields = listOf(
                app.locker.vault.data.CustomField("Expires", "2031"),
                app.locker.vault.data.CustomField("Number", "P-882103")
            ),
            createdAt = now - 420 * day,
            updatedAt = now - 420 * day
        )
        repository.createTextItem(
            type = VaultType.THING,
            title = "Car insurance",
            location = "Glove box",
            body = "Policy and roadside assistance number.",
            fields = listOf(
                app.locker.vault.data.CustomField("Policy", "CI-55219"),
                app.locker.vault.data.CustomField("Roadside", "1800-555-0142")
            ),
            createdAt = now - 120 * day,
            updatedAt = now - 120 * day
        )
        repository.createTextItem(
            type = VaultType.NOTE,
            title = "Travel plans",
            body = "Long weekend in September.\nBook the train by August.\nWater the plants before leaving.",
            createdAt = now - 28 * day,
            updatedAt = now - 28 * day
        )
    }

    fun lockNow() {
        session.lock()
    }

    private companion object {
        const val MAIN_ID = "main"
        const val DECOY_ID = "decoy"
        const val HIDDEN_ID = "hidden"
    }
}

class LockerApp : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer
        private set

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                container.session.lock()
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            // Casting or plugging in an external display is a capture risk.
            container.session.lock()
        }

        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    override fun onCreate() {
        super<Application>.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
        // Keep a chosen launcher disguise in sync after updates or restores.
        DisguiseManager.ensureApplied(this, container.preferences.settings.value.disguiseMode)
        // Remove any decrypted share/camera leftovers from a previous process.
        container.clearSensitiveCaches()
        container.appScope.launch {
            container.mainSpace.repository.purgeExpiredTrash()
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(
            displayListener,
            Handler(Looper.getMainLooper())
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        container.session.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        container.session.onAppBackgrounded()
    }
}
