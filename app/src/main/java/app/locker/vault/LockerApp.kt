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

    private val _activeSpace = MutableStateFlow(mainSpace)
    val activeSpace: StateFlow<VaultSpace> = _activeSpace.asStateFlow()
    val isDecoyActive: Boolean get() = _activeSpace.value.id == DECOY_ID

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
        return space
    }

    private fun ensureDecoySpace(): VaultSpace =
        decoySpace ?: createSpace(
            DECOY_ID,
            "locker_key_material_decoy",
            "vault_decoy.db",
            "vault_decoy"
        ).also { decoySpace = it }

    /**
     * Tries the real vault first, then the independent decoy vault. The decoy can also be
     * opened when its in-app password was changed away from the configured duress password.
     */
    suspend fun unlockWithPassword(password: CharArray): UnlockResult =
        withContext(Dispatchers.Default) {
            val main = mainSpace
            val duressConfigured = preferences.settings.value.duressVerifier.isNotBlank()
            val decoy = if (duressConfigured || decoySpace != null) ensureDecoySpace() else null

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

    private fun switchTo(space: VaultSpace) {
        if (space.id == DECOY_ID) {
            mainSpace.lockIfUnlocked()
        } else {
            decoySpace?.lockIfUnlocked()
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

    /** Full wipe: both vaults, biometric key, preferences, and in-memory state. */
    suspend fun wipeVault() {
        mainSpace.wipeData()
        decoySpace?.wipeData()
        decoySpace = null
        biometricKeyStore.deleteKey()
        preferences.reset()
        playback.clearAll()
        _activeSpace.value = mainSpace
    }

    fun clearSensitiveCaches() {
        runCatching { mainSpace.fileStore.clearSensitiveCaches() }
        runCatching { decoySpace?.fileStore?.clearSensitiveCaches() }
    }

    /** Harmless starter content for a freshly created decoy vault. */
    private suspend fun seedDecoyVault(repository: VaultRepository) {
        repository.createTextItem(
            type = VaultType.NOTE,
            title = "Grocery list",
            body = "Milk\nEggs\nBread\nCoffee beans\nLaundry detergent"
        )
        repository.createTextItem(
            type = VaultType.NOTE,
            title = "Warranty receipts",
            body = "Laptop — bought last spring, 2-year cover\nHeadphones — 1-year cover\nKeep receipts in the desk drawer."
        )
        repository.createTextItem(
            type = VaultType.SECRET,
            title = "Home Wi-Fi",
            username = "home",
            password = "sunflower-2021",
            website = "192.168.1.1"
        )
    }

    fun lockNow() {
        session.lock()
    }

    private companion object {
        const val MAIN_ID = "main"
        const val DECOY_ID = "decoy"
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
