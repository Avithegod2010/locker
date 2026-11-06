package app.locker.vault.core

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets to the clipboard with the platform's sensitive flag and clears them
 * automatically after a user-configured delay.
 */
class SecureClipboard(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null
    private var lastCopied: String? = null

    fun copy(label: String, value: String, clearAfterSeconds: Int) {
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        lastCopied = value
        clearJob?.cancel()
        if (clearAfterSeconds > 0) {
            clearJob = scope.launch {
                delay(clearAfterSeconds * 1000L)
                val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (current == lastCopied) {
                    clear()
                    Toast.makeText(
                        context,
                        "Clipboard cleared",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        lastCopied = null
    }
}
