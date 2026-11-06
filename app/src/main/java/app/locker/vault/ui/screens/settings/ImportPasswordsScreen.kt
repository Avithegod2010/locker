package app.locker.vault.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import app.locker.vault.data.PasswordImport
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline import of password exports from other password managers. The CSV is parsed
 * entirely on-device and each row is saved through the encrypted vault repository.
 */
@Composable
fun ImportPasswordsScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    SecureWindow()

    var fileName by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<PasswordImport.Parsed?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var importedCount by remember { mutableStateOf(0) }

    fun reset() {
        fileName = null
        parsed = null
        errorText = null
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        reset()
        parsing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                    PasswordImport.parse(text) to (uri.displayName(context))
                }
            }
            parsing = false
            result.onSuccess { (data, name) ->
                parsed = data
                fileName = name
            }.onFailure {
                errorText = when (it) {
                    is PasswordImport.Unrecognized -> it.message
                    else -> "The file could not be read. Export an unencrypted CSV and try again."
                }
            }
        }
    }

    fun runImport() {
        val data = parsed ?: return
        if (importing) return
        importing = true
        errorText = null
        scope.launch {
            val byFolderName = collections.associateBy { it.name.trim().lowercase() }
            var ok = 0
            data.rows.forEach { row ->
                val folderIds = row.folder.trim().takeIf { it.isNotBlank() }
                    ?.let { byFolderName[it.trim().lowercase()] }
                    ?.let { listOf(it.id) }
                    .orEmpty()
                val result = app.repository.createTextItem(
                    type = VaultType.SECRET,
                    title = row.title.ifBlank {
                        row.username.ifBlank { row.website.ifBlank { "Imported login" } }
                    },
                    body = row.notes,
                    username = row.username,
                    password = row.password,
                    website = row.website,
                    otpSecret = row.totp,
                    collectionIds = folderIds,
                    favorite = row.favorite
                )
                if (result.isSuccess) ok++
            }
            importedCount = ok
            importing = false
            parsed = null
            fileName = null
        }
    }

    SettingsScaffold(title = "Import passwords", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            icon = Icons.Rounded.Security,
            tint = BrandBlue,
            title = "Fully offline import",
            text = "Pick a CSV export from Bitwarden, 1Password, LastPass, KeePassXC, Chrome, Edge, Firefox, Samsung Internet, Dashlane, or Proton. The file is read on this device only and each entry is immediately encrypted into your vault."
        )
        Spacer(Modifier.height(8.dp))

        SettingsSectionTitle("Export file")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.NoteAdd,
                tint = BrandMint,
                title = if (parsing) "Reading file…" else "Choose CSV file",
                value = fileName ?: "Not selected",
                onClick = {
                    picker.launch(
                        arrayOf(
                            "text/comma-separated-values",
                            "text/csv",
                            "text/plain",
                            "application/csv",
                            "application/octet-stream"
                        )
                    )
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Key,
                tint = BrandBlue,
                title = if (importing) "Importing…" else "Import into vault",
                value = parsed?.let { "${it.rows.size} entries detected" } ?: "No file yet",
                onClick = { if (parsed != null && !importing) runImport() }
            )
        }

        parsed?.let { data ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Detected ${data.provider} export. ${data.rows.size} login(s) ready, ${data.skipped} empty row(s) skipped. Entries with a folder matching one of your collections are filed there automatically — others stay unfiled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (importedCount > 0 && parsed == null && !importing) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Imported $importedCount password(s) into the vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        errorText?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }

        if (importing || parsing) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (importing) "Saving entries to the encrypted vault…" else "Parsing CSV…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Uri.displayName(context: android.content.Context): String =
    runCatching {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull().orEmpty().ifBlank { "Selected file" }
