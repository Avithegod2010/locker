package app.locker.vault.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.locker.vault.data.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs one automatic encrypted snapshot per day into a folder the user picked. The snapshot
 * uses the vault password the user just typed, so it can be restored with the same password.
 * The password copy is held only until the backup finishes and is wiped afterwards.
 */
class AutoBackupRunner(
    context: Context,
    private val preferences: AppPreferences,
    private val backupManager: BackupManager,
    private val session: SessionManager
) {
    private val appContext = context.applicationContext

    suspend fun maybeRun() = withContext(Dispatchers.IO) {
        val settings = preferences.settings.value
        if (!settings.autoBackupEnabled) return@withContext
        val treeUri = settings.autoBackupTreeUri.takeIf { it.isNotBlank() } ?: return@withContext
        val now = System.currentTimeMillis()
        val interval = 24L * 60L * 60L * 1000L
        if (now - settings.autoBackupLastAt < interval) return@withContext
        val password = session.takeUnlockPasswordForBackup() ?: return@withContext
        try {
            val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
                ?: return@withContext
            if (!tree.canWrite()) return@withContext
            val name = "Locker-backup-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now)) +
                ".locker"
            val target = tree.createFile("application/octet-stream", name)
                ?: return@withContext
            val verifyPassword = password.copyOf()
            val result = backupManager.createBackup(target.uri, password)
            if (result.isFailure) {
                verifyPassword.fill('\u0000')
                target.delete()
                return@withContext
            }
            // Restore drill: read the archive back before trusting it.
            val verified = runCatching {
                backupManager.inspect(target.uri, verifyPassword)
            }.getOrNull()
            verifyPassword.fill('\u0000')
            if (verified == null || verified.isFailure) {
                target.delete()
                return@withContext
            }
            preferences.setAutoBackupLastAt(now)
            prune(tree, settings.autoBackupKeep)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun prune(tree: DocumentFile, keep: Int) {
        val files = tree.listFiles()
            .filter { file ->
                file.isFile && file.name?.startsWith("Locker-backup-") == true
            }
            .sortedByDescending { it.name.orEmpty() }
        files.drop(keep.coerceAtLeast(2)).forEach { runCatching { it.delete() } }
    }
}
