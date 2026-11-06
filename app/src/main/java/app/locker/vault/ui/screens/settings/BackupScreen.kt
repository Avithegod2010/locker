package app.locker.vault.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.Formatting
import app.locker.vault.data.BackupPreview
import app.locker.vault.data.BackupSummary
import app.locker.vault.data.RestoreMode
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.PrimaryButton
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SecureWindow()

    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createPassword by remember { mutableStateOf("") }
    var createConfirm by remember { mutableStateOf("") }
    var pendingCreatePassword by remember { mutableStateOf("") }
    var showRestorePassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var showReplaceConfirm by remember { mutableStateOf(false) }
    var lastSummary by remember { mutableStateOf<BackupSummary?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        app.session.endExternalActivity()
        if (uri == null) {
            pendingCreatePassword = ""
            return@rememberLauncherForActivityResult
        }
        val password = pendingCreatePassword
        pendingCreatePassword = ""
        if (password.length < 10) return@rememberLauncherForActivityResult
        progress = 0f
        error = null
        status = null
        scope.launch {
            val result = app.backupManager.createBackup(
                uri = uri,
                password = password.toCharArray(),
                onProgress = { progress = it }
            )
            progress = null
            result.onSuccess { info ->
                status = "Backup created · ${info.itemCount} items · ${Formatting.bytes(info.totalBytes)}"
            }.onFailure { error = it.message ?: "Could not create backup" }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        app.session.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        restoreUri = uri
        restorePassword = ""
        showRestorePassword = true
    }

    SettingsScaffold(title = "Backup & restore", onBack = onBack) {
        Spacer(Modifier.height(6.dp))
        InfoBanner(
            icon = Icons.Rounded.CloudOff,
            tint = BrandMint,
            title = "Your backup never leaves your control",
            text = "Locker writes one encrypted .locker file to the destination you choose. There is no cloud account and the app cannot upload anything."
        )

        SettingsSectionTitle("Create")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Archive,
                tint = BrandBlue,
                title = "Create encrypted backup",
                subtitle = "Items, collections, profile, and every attached file.",
                onClick = {
                    createPassword = ""
                    createConfirm = ""
                    showCreateDialog = true
                }
            )
        }

        SettingsSectionTitle("Restore")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Restore,
                tint = BrandViolet,
                title = "Restore from a .locker file",
                subtitle = "Preview the archive first, then merge or replace.",
                onClick = {
                    error = null
                    status = null
                    preview = null
                    app.session.beginExternalActivity()
                    openLauncher.launch(arrayOf("*/*"))
                }
            )
        }

        if (preview != null) {
            Spacer(Modifier.height(14.dp))
            RestorePreviewCard(
                preview = preview!!,
                mode = restoreMode,
                onMode = { restoreMode = it },
                onRestore = {
                    if (restoreMode == RestoreMode.REPLACE) showReplaceConfirm = true
                    else performRestore(
                        app, restoreUri, restorePassword, restoreMode,
                        onProgress = { progress = it },
                        onDone = { summary ->
                            progress = null
                            lastSummary = summary
                            status = "Restored ${summary.itemsRestored} items and ${summary.filesRestored} files"
                            preview = null
                        },
                        onError = { error = it; progress = null }
                    )
                }
            )
        }

        if (lastSummary != null) {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BrandMint.copy(alpha = 0.11f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(15.dp)) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = BrandMint
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Restore complete",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${lastSummary!!.itemsRestored} items · " +
                                "${lastSummary!!.collectionsRestored} collections · " +
                                "${lastSummary!!.filesRestored} files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (status != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                status ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = BrandMint
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "The backup password is separate from your vault password. Without it, the file cannot be opened, even by us.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(34.dp))
    }

    if (progress != null) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (preview == null) "Encrypting backup" else "Restoring") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${((progress ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (showCreateDialog) {
        BackupPasswordDialog(
            title = "Backup password",
            description = "Use a long, unique password. You will need it to restore this file.",
            password = createPassword,
            confirm = createConfirm,
            onPassword = { createPassword = it },
            onConfirm = { createConfirm = it },
            onDismiss = { showCreateDialog = false },
            onContinue = {
                pendingCreatePassword = createPassword
                showCreateDialog = false
                app.session.beginExternalActivity()
                createLauncher.launch(app.backupManager.suggestedFileName())
            }
        )
    }

    if (showRestorePassword && restoreUri != null) {
        BackupPasswordDialog(
            title = "Open backup",
            description = "Enter the password used when this .locker file was created.",
            password = restorePassword,
            confirm = null,
            onPassword = { restorePassword = it },
            onConfirm = {},
            onDismiss = { showRestorePassword = false; restoreUri = null },
            onContinue = {
                val uri = restoreUri
                if (uri == null) return@BackupPasswordDialog
                progress = 0f
                error = null
                scope.launch {
                    val result = app.backupManager.inspect(uri, restorePassword.toCharArray())
                    progress = null
                    result.onSuccess {
                        preview = it
                        showRestorePassword = false
                    }.onFailure {
                        error = it.message ?: "Could not open backup"
                    }
                }
            }
        )
    }

    if (showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            icon = { androidx.compose.material3.Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text("Replace this vault?") },
            text = {
                Text(
                    "Every current item and file will be deleted, then the backup will be written in its place. This cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirm = false
                    performRestore(
                        app, restoreUri, restorePassword, RestoreMode.REPLACE,
                        onProgress = { progress = it },
                        onDone = { summary ->
                            progress = null
                            lastSummary = summary
                            status = "Restored ${summary.itemsRestored} items and ${summary.filesRestored} files"
                            preview = null
                        },
                        onError = { error = it; progress = null }
                    )
                }) { Text("Replace vault", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun performRestore(
    app: app.locker.vault.AppContainer,
    uri: android.net.Uri?,
    password: String,
    mode: RestoreMode,
    onProgress: (Float) -> Unit,
    onDone: (BackupSummary) -> Unit,
    onError: (String) -> Unit
) {
    if (uri == null) {
        onError("No backup selected")
        return
    }
    app.appScope.launch {
        val result = app.backupManager.restore(
            uri = uri,
            password = password.toCharArray(),
            mode = mode,
            onProgress = onProgress
        )
        result.onSuccess(onDone).onFailure { onError(it.message ?: "Could not restore backup") }
    }
}

@Composable
private fun RestorePreviewCard(
    preview: BackupPreview,
    mode: RestoreMode,
    onMode: (RestoreMode) -> Unit,
    onRestore: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Backup preview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${preview.itemCount} items · ${preview.collectionCount} collections · " +
                    "${preview.fileCount} files · ${Formatting.bytes(preview.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Created ${Formatting.fullDate(preview.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            if (!preview.matchesCurrentVault) {
                Text(
                    "This backup belongs to a different vault and cannot be restored here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    ModeChip("Merge", mode == RestoreMode.MERGE) { onMode(RestoreMode.MERGE) }
                    ModeChip("Replace", mode == RestoreMode.REPLACE) { onMode(RestoreMode.REPLACE) }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(text = "Restore backup", onClick = onRestore)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    password: String,
    confirm: String?,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val valid = password.length >= 10 && (confirm == null || password == confirm)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                Modifier
                    .height(440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                LockerTextField(
                    value = password,
                    onValueChange = onPassword,
                    placeholder = "At least 10 characters",
                    label = "Password",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    isSensitive = true
                )
                if (confirm != null) {
                    Spacer(Modifier.height(14.dp))
                    LockerTextField(
                        value = confirm,
                        onValueChange = onConfirm,
                        placeholder = "Repeat the password",
                        label = "Confirm",
                        required = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        isSensitive = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue, enabled = valid) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
