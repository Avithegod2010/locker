package app.locker.vault

import android.graphics.Color
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.locker.vault.core.toSharedContent
import app.locker.vault.ui.LockerRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var screenRecordingCallback: java.util.function.Consumer<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // A vault password must never be offered to a system/cloud autofill provider.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        // Apply secure-window protection before the first frame, not after composition.
        if ((application as LockerApp).container.preferences.settings.value.blockScreenshots) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        val container = (application as LockerApp).container
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 35) {
            val callback = java.util.function.Consumer<Int> { state ->
                if (state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE) {
                    container.lockNow()
                }
            }
            screenRecordingCallback = callback
            runCatching {
                windowManager.addScreenRecordingCallback(mainExecutor, callback)
            }.onFailure {
                screenRecordingCallback = null
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.preferences.settings
                    .map { it.blockScreenshots }
                    .distinctUntilChanged()
                    .collect { block ->
                        if (block) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
            }
        }

        setContent {
            LockerRoot(container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        screenRecordingCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= 35) {
                runCatching { windowManager.removeScreenRecordingCallback(callback) }
            }
        }
        screenRecordingCallback = null
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val container = (application as LockerApp).container
        when (intent.getStringExtra("shortcut")) {
            "lock" -> container.lockNow()
            "new_note" -> container.pendingRoute.value = "new/NOTE"
            "new_secret" -> container.pendingRoute.value = "new/SECRET"
            "new_photo" -> container.pendingRoute.value = "import/photo"
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme != "locker") {
                runCatching {
                    if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                val mime = intent.type ?: contentResolver.getType(uri).orEmpty()
                val visual = mime.startsWith("image/") || mime.startsWith("video/")
                container.pendingImport.value = listOf(uri)
                container.pendingRoute.value = if (visual) "import/photo" else "import/file"
            }
        }
        intent.toSharedContent()?.let { content ->
            if (!content.isEmpty) container.pendingShare.value = content
        }
        if (intent.action == Intent.ACTION_SEND ||
            intent.action == Intent.ACTION_SEND_MULTIPLE ||
            intent.action == Intent.ACTION_VIEW
        ) {
            // Prevent reprocessing the same share/open intent after an activity recreation.
            setIntent(Intent(this, MainActivity::class.java))
        }
    }
}
