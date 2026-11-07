package app.locker.vault.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught-exception reports to an app-private file and nowhere else. The file is
 * capped so a crash loop cannot fill storage, and it is removed with the rest of the app data.
 */
object CrashReporter {
    private const val MAX_BYTES = 200_000
    private const val TRIM_TO_BYTES = 100_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun file(context: Context): File = File(context.filesDir, "diagnostics/crash.log")

    fun read(context: Context): String {
        val file = file(context)
        if (!file.exists()) return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val target = file(context)
        target.parentFile?.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = buildString {
            append("=== ").append(stamp)
            append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" · Android ").append(Build.VERSION.RELEASE)
            append(" · Locker ").append(app.locker.vault.BuildConfig.VERSION_NAME)
            append(" · thread ").append(thread.name)
            append(" ===\n")
        }
        val body = throwable.stackTraceToString()
        val existing = if (target.exists() && target.length() > MAX_BYTES) {
            runCatching { target.readText().takeLast(TRIM_TO_BYTES) }.getOrDefault("")
        } else {
            runCatching { target.readText() }.getOrDefault("")
        }
        runCatching {
            target.writeText(existing + header + body + "\n\n")
        }
    }
}
