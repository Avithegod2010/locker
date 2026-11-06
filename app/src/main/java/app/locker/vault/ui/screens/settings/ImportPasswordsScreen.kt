package app.locker.vault.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.data.PasswordImport
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline password import from other managers: CSV, KeePass XML, or 1Password .1pux.
 * Selected rows are previewed and de-duplicated before anything is written to the vault.
 */
@Composable
fun ImportPasswordsScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    SecureWindow()

    var fileName by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<PasswordImport.Parsed?>(null) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var duplicates by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var parsing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var importedCount by remember { mutableStateOf(0) }

    fun reset() {
        fileName = null
        parsed = null
        selected = emptySet()
        duplicates = emptySet()
        errorText = null
    }

    fun duplicateOf(row: PasswordImport.Row): Boolean {
        if (row.title.isBlank() && row.username.isBlank()) return false
        return items.any { item ->
            item.type == VaultType.SECRET &&
                item.deletedAt == null &&
                item.title.equals(row.title, ignoreCase = true) &&
                item.username.equals(row.username, ignoreCase = true)
        }
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
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: ByteArray(0)
                    PasswordImport.parseBytes(bytes) to uri.displayName(context)
                }
            }
            parsing = false
            result.onSuccess { (data, name) ->
                parsed = data
                fileName = name
                val duplicateIndexes = data.rows.indices
                    .filter { index -> duplicateOf(data.rows[index]) }
                    .toSet()
                duplicates = duplicateIndexes
                selected = data.rows.indices.filterNot { it in duplicateIndexes }.toSet()
            }.onFailure {
                errorText = when (it) {
                    is PasswordImport.Unrecognized -> it.message
                    else -> "The file could not be read. Export an unencrypted CSV, KeePass XML, or 1PUX file."
                }
            }
        }
    }

    fun toggle(index: Int) {
        selected = if (index in selected) selected - index else selected + index
    }

    fun runImport() {
        val data = parsed ?: return
        if (importing || selected.isEmpty()) return
        importing = true
        errorText = null
        scope.launch {
            val byFolderName = collections.associateBy { it.name.trim().lowercase() }
            val rows = data.rows.filterIndexed { index, _ -> index in selected }
            var ok = 0
            rows.forEach { row ->
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
            selected = emptySet()
            fileName = null
        }
    }

    SettingsScaffold(title = "Import passwords", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            icon = Icons.Rounded.Security,
            tint = BrandBlue,
            title = "Fully offline import",
            text = "Pick a CSV export, a KeePass XML file, or a 1Password .1pux archive. The file is read on this device only; nothing is uploaded. Columns are mapped automatically, and you can untick anything before it is encrypted into the vault."
        )
        Spacer(Modifier.height(8.dp))

        SettingsSectionTitle("Export file")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.NoteAdd,
                tint = BrandMint,
                title = if (parsing) "Reading file…" else "Choose export file",
                value = fileName ?: "Not selected",
                onClick = {
                    picker.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "text/xml",
                            "application/xml",
                            "application/zip",
                            "application/octet-stream",
                            "text/plain"
                        )
                    )
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.FileDownload,
                tint = BrandViolet,
                title = if (importing) "Importing…" else "Import selected",
                value = parsed?.let { "${selected.size} of ${it.rows.size} selected" } ?: "No file yet",
                onClick = { if (parsed != null && !importing && selected.isNotEmpty()) runImport() }
            )
        }

        parsed?.let { data ->
            Spacer(Modifier.height(10.dp))
            Text(
                "Detected ${data.provider} export · ${data.rows.size} login(s), ${data.skipped} empty row(s) skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Auto-mapped: title · username · password · website · notes · TOTP · folder · favorite",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${duplicates.size} duplicate(s) found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    selected = data.rows.indices.toSet()
                }) { Text("All") }
                TextButton(onClick = { selected = emptySet() }) { Text("None") }
            }
            SettingsGroup {
                data.rows.take(120).forEachIndexed { index, row ->
                    if (index > 0) RowDivider()
                    ImportPreviewRow(
                        row = row,
                        checked = index in selected,
                        duplicate = index in duplicates,
                        onToggle = { toggle(index) }
                    )
                }
            }
            if (data.rows.size > 120) {
                Text(
                    "Showing the first 120 rows of ${data.rows.size}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (importedCount > 0 && parsed == null && !importing) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Imported $importedCount password(s) into the vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
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
                    if (importing) "Saving entries to the encrypted vault…" else "Parsing export…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImportPreviewRow(
    row: PasswordImport.Row,
    checked: Boolean,
    duplicate: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title.ifBlank {
                        row.username.ifBlank { row.website.ifBlank { "Imported login" } }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (duplicate) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "Duplicate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (row.username.isNotBlank() || row.website.isNotBlank()) {
                Text(
                    row.username.ifBlank { row.website },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
