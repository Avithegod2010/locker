package app.locker.vault.core

import android.os.Build
import java.io.File

/**
 * Best-effort root/tamper signal used only to warn the user. This is not a security boundary:
 * a determined attacker can hide root, and false positives are possible on custom ROMs.
 */
object DeviceIntegrity {
    private val suPaths = listOf(
        "/system/app/Superuser.apk",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/magisk/.core/bin/su"
    )

    fun isProbablyRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        if (suPaths.any { path -> runCatching { File(path).exists() }.getOrDefault(false) }) {
            return true
        }
        return runCatching {
            val process = ProcessBuilder("which", "su")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
