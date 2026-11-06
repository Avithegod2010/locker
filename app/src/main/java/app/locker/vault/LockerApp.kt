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
import app.locker.vault.core.BiometricKeyStore
import app.locker.vault.core.DisguiseManager
import app.locker.vault.core.KeyMaterialStore
import app.locker.vault.core.SecureClipboard
import app.locker.vault.core.SessionManager
import app.locker.vault.data.EncryptedFileStore
import app.locker.vault.data.BackupManager
import app.locker.vault.data.VaultDb
import app.locker.vault.data.VaultRepository
import app.locker.vault.ui.components.ThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val preferences = AppPreferences(appContext)
    val keyMaterialStore = KeyMaterialStore(appContext)
    val biometricKeyStore = BiometricKeyStore(appContext)
    val fileStore = EncryptedFileStore(appContext)
    val secureClipboard = SecureClipboard(appContext, appScope)
    val playback = app.locker.vault.core.PlaybackMemory(appContext)

    /** Content sent to Locker through the Android share sheet, waiting for the UI. */
    val pendingShare = MutableStateFlow<app.locker.vault.core.SharedContent?>(null)

    /** URIs handed from the share sheet to the multi-file import screen. */
    val pendingImport = MutableStateFlow<List<android.net.Uri>>(emptyList())

    /** A shortcut route to open once the vault is unlocked. */
    val pendingRoute = MutableStateFlow<String?>(null)

    /** A search query handed to the home screen (smart folders). */
    val pendingSearch = MutableStateFlow<String?>(null)

    private val db = VaultDb(appContext)
    val database: VaultDb get() = db
    val session = SessionManager(
        preferences = preferences,
        keyMaterialStore = keyMaterialStore,
        biometricKeyStore = biometricKeyStore,
        fileStore = fileStore,
        scope = appScope
    )
    val repository = VaultRepository(
        context = appContext,
        db = db,
        files = fileStore,
        session = session,
        preferences = preferences
    )
    val backupManager = BackupManager(
        context = appContext,
        db = db,
        files = fileStore,
        repository = repository,
        session = session
    )
    val autoBackup = app.locker.vault.core.AutoBackupRunner(
        context = appContext,
        preferences = preferences,
        backupManager = backupManager,
        session = session
    )

    init {
        session.onVaultCreated = {
            repository.seedDefaults()
        }
        session.onWipeRequired = {
            wipeVault()
        }
        session.onLocked = {
            repository.clearInMemory()
            ThumbnailCache.clear()
            secureClipboard.clear()
        }
        session.onDuressWipe = {
            repository.wipeAllData()
            biometricKeyStore.deleteKey()
            keyMaterialStore.wipe()
            playback.clearAll()
            ThumbnailCache.clear()
            secureClipboard.clear()
            preferences.reset()
        }
        session.onDecoyReady = {
            seedDecoyVault()
        }
    }

    /** Harmless starter content for a freshly created decoy vault. */
    private suspend fun seedDecoyVault() {
        repository.createTextItem(
            type = app.locker.vault.data.VaultType.NOTE,
            title = "Grocery list",
            body = "Milk\nEggs\nBread\nCoffee beans\nLaundry detergent"
        )
        repository.createTextItem(
            type = app.locker.vault.data.VaultType.NOTE,
            title = "Warranty receipts",
            body = "Laptop — bought last spring, 2-year cover\nHeadphones — 1-year cover\nKeep receipts in the desk drawer."
        )
        repository.createTextItem(
            type = app.locker.vault.data.VaultType.SECRET,
            title = "Home Wi-Fi",
            username = "home",
            password = "sunflower-2021",
            website = "192.168.1.1"
        )
    }

    suspend fun wipeVault() {
        repository.wipeAllData()
        biometricKeyStore.deleteKey()
        keyMaterialStore.wipe()
        preferences.reset()
        session.resetToSetup()
    }

    fun lockNow() {
        session.lock()
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
        container = AppContainer(this)
        // Keep a chosen launcher disguise in sync after updates or restores.
        DisguiseManager.ensureApplied(this, container.preferences.settings.value.disguiseMode)
        // Remove any decrypted share/camera leftovers from a previous process.
        container.fileStore.clearSensitiveCaches()
        container.appScope.launch {
            container.repository.purgeExpiredTrash()
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
