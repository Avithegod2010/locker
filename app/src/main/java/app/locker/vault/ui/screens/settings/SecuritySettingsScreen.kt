package app.locker.vault.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.locker.vault.BiometricAuthActivity
import app.locker.vault.core.Crypto
import app.locker.vault.core.DeviceIntegrity
import app.locker.vault.core.DisguiseManager
import app.locker.vault.core.RecoveryCodeFormat
import app.locker.vault.core.RecoveryKitPdf
import app.locker.vault.core.UnlockResult
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandCoral
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPasswordHealth: () -> Unit,
    onOpenImport: () -> Unit
) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SecureWindow()

    var recoveryKitCode by remember { mutableStateOf<String?>(null) }
    val recoveryKitLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val code = recoveryKitCode
        recoveryKitCode = null
        if (uri == null || code == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        RecoveryKitPdf.write(output, code)
                    } != null
                }.getOrDefault(false)
            }
            android.widget.Toast.makeText(
                context,
                if (ok) "Recovery kit saved" else "Could not save recovery kit",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    val biometricLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val message = result.data?.getStringExtra(BiometricAuthActivity.EXTRA_MESSAGE)
        android.widget.Toast.makeText(
            context,
            when {
                app.preferences.settings.value.biometricEnabled -> "Biometric unlock enabled"
                message != null && message != "Cancelled" -> message
                else -> "Biometric unlock was not enabled"
            },
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    var showChangePassword by remember { mutableStateOf(false) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showClipboard by remember { mutableStateOf(false) }
    var showWipeAttempts by remember { mutableStateOf(false) }
    var showWipeVault by remember { mutableStateOf(false) }
    var showRecoveryKey by remember { mutableStateOf(false) }
    var recoveryKeyText by remember { mutableStateOf<String?>(null) }
    var showDisguise by remember { mutableStateOf(false) }
    var showCalculatorCode by remember { mutableStateOf(false) }
    var showDuress by remember { mutableStateOf(false) }
    var calculatorCode by remember { mutableStateOf("") }
    var duressPassword by remember { mutableStateOf("") }
    var duressConfirm by remember { mutableStateOf("") }

    val biometricAvailable = remember { app.biometricKeyStore.canAuthenticate() }
    val deviceRooted = remember { DeviceIntegrity.isProbablyRooted() }
    val autoLockOptions = listOf(
        ChoiceOption("Immediately", "Lock as soon as Locker leaves the foreground"),
        ChoiceOption("After 30 seconds"),
        ChoiceOption("After 1 minute"),
        ChoiceOption("After 5 minutes"),
        ChoiceOption("After 15 minutes")
    )
    val autoLockValues = listOf(0, 30, 60, 300, 900)
    val clipboardOptions = listOf(
        ChoiceOption("After 15 seconds"),
        ChoiceOption("After 30 seconds"),
        ChoiceOption("After 1 minute"),
        ChoiceOption("After 2 minutes"),
        ChoiceOption("Never")
    )
    val clipboardValues = listOf(15, 30, 60, 120, 0)
    val disguiseIds = listOf(
        DisguiseManager.ID_NONE,
        DisguiseManager.ID_CALCULATOR,
        DisguiseManager.ID_CALENDAR,
        DisguiseManager.ID_NOTES
    )
    val disguiseOptions = listOf(
        ChoiceOption("Locker", "The real app: shield icon and the name Locker"),
        ChoiceOption("Calculator", "Plain calculator icon and name on the home screen"),
        ChoiceOption("Calendar", "Calendar icon and name on the home screen"),
        ChoiceOption("Notes", "Notes icon and name on the home screen")
    )
    val wipeOptions = listOf(
        ChoiceOption("Off", "Never erase automatically"),
        ChoiceOption("After 5 failed attempts"),
        ChoiceOption("After 10 failed attempts"),        ChoiceOption("After 15 failed attempts")
    )
    val wipeValues = listOf(0, 5, 10, 15)

    SettingsScaffold(title = "Security", onBack = onBack) {
        if (deviceRooted) {
            InfoBanner(
                icon = Icons.Rounded.Warning,
                tint = BrandCoral,
                title = "Device integrity warning",
                text = "This phone looks rooted or is running a test build. On modified systems, screenshot blocking and Android Keystore protections are weaker than usual. Locker still encrypts everything, but the operating system itself may be able to inspect the unlocked app."
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        InfoBanner(
            icon = Icons.Rounded.Shield,
            tint = BrandMint,
            title = "Your vault is protected",
            text = "AES-256-GCM authenticates every record. Your password is stretched with Argon2id and the vault key only exists in memory while unlocked."
        )

        if (settings.recoveryUsed) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BrandAmber.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Recovery access is active",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Set a fresh password now. Your recovery key stays valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        onClick = { showChangePassword = true },
                        shape = RoundedCornerShape(14.dp),
                        color = BrandAmber.copy(alpha = 0.18f)
                    ) {
                        Text(
                            "Set new password",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandAmber,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        SettingsSectionTitle("Access")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Key,
                tint = BrandBlue,
                title = "Change vault password",
                subtitle = "Re-wraps your vault key. Your recovery key stays valid.",
                onClick = { showChangePassword = true }
            )
            RowDivider()
            SettingSwitchRow(
                icon = Icons.Rounded.Fingerprint,
                tint = BrandViolet,
                title = "Biometric unlock",
                subtitle = if (biometricAvailable) {
                    "Use a hardware-backed Keystore key that requires a fresh authentication."
                } else {
                    "No compatible fingerprint or face unlock is enrolled."
                },
                checked = settings.biometricEnabled,
                enabled = biometricAvailable || settings.biometricEnabled,
                onCheckedChange = { enable ->
                    if (enable) {
                        runCatching {
                            biometricLauncher.launch(
                                android.content.Intent(context, BiometricAuthActivity::class.java)
                                    .putExtra(
                                        BiometricAuthActivity.EXTRA_MODE,
                                        BiometricAuthActivity.MODE_ENROLL
                                    )
                            )
                        }.onFailure {
                            android.widget.Toast.makeText(
                                context,
                                "Biometric unlock is unavailable.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        app.session.disableBiometrics()
                        android.widget.Toast.makeText(
                            context,
                            "Biometric unlock disabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Lock,
                tint = BrandBlue,
                title = "Autofill in other apps",
                subtitle = "Let Locker fill saved logins while this vault is unlocked.",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }.onFailure {
                        android.widget.Toast.makeText(
                            context,
                            "Open Settings → Autofill to choose Locker",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Lock,
                tint = BrandAmber,
                title = "Lock vault now",
                subtitle = "Clear the vault key from memory immediately.",
                onClick = { app.lockNow() }
            )
        }

        SettingsSectionTitle("Data")
        SettingsGroup {
            if (!app.isDecoyActive) {
                SettingRow(
                    icon = Icons.Rounded.Archive,
                    tint = BrandBlue,
                    title = "Encrypted backup & restore",
                    subtitle = "Create or restore a .locker archive. The app never uploads it.",
                    onClick = onOpenBackup
                )
                RowDivider()
            }
            SettingRow(
                icon = Icons.Rounded.Key,
                tint = BrandCoral,
                title = "Password health",
                subtitle = "Find weak, reused, common, and year-old passwords.",
                onClick = onOpenPasswordHealth
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.NoteAdd,
                tint = BrandMint,
                title = "Import passwords from CSV",
                subtitle = "Bring logins over from Bitwarden, 1Password, LastPass, Chrome, and more. Parsed and encrypted fully offline.",
                onClick = onOpenImport
            )
        }

        SettingsSectionTitle("Launcher disguise")
        InfoBanner(
            icon = Icons.Rounded.Shield,
            tint = BrandViolet,
            title = "Disguised on the home screen",
            text = "A disguise swaps the icon and the app name everywhere on this phone. Only one icon is shown at a time — the Calculator, Calendar, and Notes entries replace Locker's own. If your launcher keeps the old icon, tap Refresh launcher icon below. Opening a disguise still shows Locker's lock screen — your data stays encrypted."
        )
        Spacer(Modifier.height(10.dp))
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Shield,
                tint = BrandViolet,
                title = "Show as",
                subtitle = "The icon and name other people see in the app drawer.",
                value = DisguiseManager.labelFor(settings.disguiseMode),
                onClick = { showDisguise = true }
            )
            if (settings.disguiseMode != DisguiseManager.ID_NONE) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Calculate,
                    tint = BrandBlue,
                    title = "Disguise passcode",
                    subtitle = "Used by Calculator, Notes, and Calendar. Long-press the title in Notes or Calendar.",
                    value = if (app.preferences.hasCustomCalculatorCode()) "Custom" else "1234",
                    onClick = {
                        calculatorCode = ""
                        showCalculatorCode = true
                    }
                )
            }
            if (settings.disguiseMode != DisguiseManager.ID_NONE) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Refresh,
                    tint = BrandMint,
                    title = "Refresh launcher icon",
                    subtitle = "Use this if your launcher still shows the old icon or name.",
                    onClick = {
                        DisguiseManager.refresh(context, settings.disguiseMode)
                        android.widget.Toast.makeText(
                            context,
                            "Launcher refreshed — give it a few seconds",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        SettingsSectionTitle("Duress")
        if (!app.isDecoyActive) {
            InfoBanner(
                icon = Icons.Rounded.Shield,
                tint = BrandMint,
                title = "A decoy vault, not a wipe",
                text = "The duress password opens a separate, harmless vault with example entries. Your real vault stays encrypted and untouched, and your normal password still opens it. The decoy has its own independent keys, database, and files."
            )
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                SettingRow(
                    icon = Icons.Rounded.Key,
                    tint = BrandCoral,
                    title = "Duress password",
                    subtitle = "Opens a separate decoy vault; the real vault stays intact.",
                    value = if (settings.duressVerifier.isNotBlank()) "On" else "Off",
                    onClick = {
                        duressPassword = ""
                        duressConfirm = ""
                        showDuress = true
                    }
                )
                if (settings.duressVerifier.isNotBlank()) {
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Key,
                        tint = BrandMint,
                        title = "Remove duress password",
                        subtitle = "Deletes the decoy vault. Your real vault is unaffected.",
                        onClick = {
                            scope.launch {
                                app.clearDuressPassword()
                                android.widget.Toast.makeText(
                                    context,
                                    "Duress password removed",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        } else {
            InfoBanner(
                icon = Icons.Rounded.Shield,
                tint = BrandViolet,
                title = "This is the decoy vault",
                text = "Settings that could reveal or affect the real vault are hidden here. Use your normal password on the lock screen to return to the real vault."
            )
        }

        SettingsSectionTitle("Locking")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Lock,
                tint = BrandBlue,
                title = "Auto-lock",
                subtitle = "How long Locker may stay unlocked in the background.",
                value = autoLockLabel(settings.autoLockSeconds),
                onClick = { showAutoLock = true }
            )
            RowDivider()
            SettingSwitchRow(
                icon = Icons.Rounded.VisibilityOff,
                tint = BrandCoral,
                title = "Prevent screenshots",
                subtitle = "Hide vault contents from screenshots and the recent-apps preview.",
                checked = settings.blockScreenshots,
                onCheckedChange = { app.preferences.setBlockScreenshots(it) }
            )
            RowDivider()
            SettingSwitchRow(
                icon = Icons.Rounded.ContentCopy,
                tint = BrandMint,
                title = "Clear clipboard automatically",
                subtitle = "Removes copied passwords after the selected delay.",
                checked = settings.clipboardClearSeconds > 0,
                onCheckedChange = { enabled ->
                    app.preferences.setClipboardClearSeconds(if (enabled) 30 else 0)
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.ContentCopy,
                tint = BrandMint,
                title = "Clipboard timeout",
                value = clipboardLabel(settings.clipboardClearSeconds),
                onClick = { showClipboard = true }
            )
        }

        InfoBanner(
            icon = Icons.Rounded.VisibilityOff,
            tint = BrandAmber,
            title = "Screen turns black while typing?",
            text = "On some Samsung phones with One UI, apps that block screenshots can briefly fade the keyboard area to black while you type. This is a device quirk — nothing is broken and the physical screen shows your input normally. If it bothers you, turn \"Prevent screenshots\" off temporarily while editing."
        )

        SettingsSectionTitle("Recovery")
        InfoBanner(
            icon = Icons.Rounded.Key,
            tint = BrandAmber,
            title = "Recovery key",
            text = "A recovery key was generated when this vault was created. It can unlock the vault if you forget your password. Keep your written copy somewhere safe; you can also view it while the vault is unlocked."
        )
        Spacer(Modifier.height(10.dp))
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Key,
                tint = BrandAmber,
                title = "View recovery key",
                subtitle = "Reveal the encrypted copy stored inside this vault.",
                onClick = {
                    val master = runCatching { app.session.requireMasterKey() }.getOrNull()
                    if (master == null) {
                        android.widget.Toast.makeText(context, "Unlock the vault first", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        recoveryKeyText = try {
                            app.keyMaterialStore.recoveryCodeCopy(master)
                        } finally {
                            Crypto.wipe(master)
                        }
                        if (recoveryKeyText == null) {
                            android.widget.Toast.makeText(
                                context,
                                "No stored recovery key copy (older vault)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showRecoveryKey = true
                        }
                    }
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Key,
                tint = BrandCoral,
                title = "Rotate recovery key",
                subtitle = "Issue a new key and invalidate the old written copy.",
                onClick = {
                    val rotated = app.session.rotateRecoveryKey()
                    if (rotated == null) {
                        android.widget.Toast.makeText(
                            context,
                            "Could not rotate recovery key",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        recoveryKeyText = rotated
                        showRecoveryKey = true
                        android.widget.Toast.makeText(
                            context,
                            "New recovery key issued. The old key no longer works.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.NoteAdd,
                tint = BrandBlue,
                title = "Save recovery kit (PDF)",
                subtitle = "Print a one-page kit with the key and recovery instructions.",
                onClick = {
                    val master = runCatching { app.session.requireMasterKey() }.getOrNull()
                    if (master == null) {
                        android.widget.Toast.makeText(context, "Unlock the vault first", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val code = try {
                            app.keyMaterialStore.recoveryCodeCopy(master)
                        } finally {
                            Crypto.wipe(master)
                        }
                        if (code == null) {
                            android.widget.Toast.makeText(
                                context,
                                "No stored recovery key copy (older vault)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            recoveryKitCode = code
                            recoveryKitLauncher.launch("Locker-recovery-kit.pdf")
                        }
                    }
                }
            )
        }

        SettingsSectionTitle("Intrusion protection")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Warning,
                tint = BrandCoral,
                title = "Erase vault after failed attempts",
                subtitle = "Dangerous: this permanently wipes all data on this device.",
                value = if (settings.wipeAfterAttempts == 0) "Off" else "${settings.wipeAfterAttempts} tries",
                onClick = { showWipeAttempts = true }
            )
        }

        if (!app.isDecoyActive) {
            SettingsSectionTitle("Danger zone")
            DangerZoneCard(
                title = "Erase this vault",
                description = "Permanently delete every item, file, collection, and key from this device. There is no undo and no cloud copy.",
                actionLabel = "Erase vault",
                onClick = { showWipeVault = true }
            )
        }

        Spacer(Modifier.height(34.dp))
    }

    if (showChangePassword) {
        ChangePasswordDialog(onDismiss = { showChangePassword = false })
    }

    if (showAutoLock) {
        ChoiceDialog(
            title = "Auto-lock",
            options = autoLockOptions,
            selectedIndex = autoLockValues.indexOf(settings.autoLockSeconds).coerceAtLeast(0),
            onSelect = { index -> app.preferences.setAutoLockSeconds(autoLockValues[index]) },
            onDismiss = { showAutoLock = false }
        )
    }

    if (showClipboard) {
        ChoiceDialog(
            title = "Clear clipboard",
            options = clipboardOptions,
            selectedIndex = clipboardValues.indexOf(settings.clipboardClearSeconds).coerceAtLeast(0),
            onSelect = { index -> app.preferences.setClipboardClearSeconds(clipboardValues[index]) },
            onDismiss = { showClipboard = false }
        )
    }

    if (showDisguise) {
        ChoiceDialog(
            title = "Launcher disguise",
            options = disguiseOptions,
            selectedIndex = disguiseIds.indexOf(settings.disguiseMode).coerceAtLeast(0),
            onSelect = { index ->
                val id = disguiseIds[index]
                app.preferences.setDisguiseMode(id)
                DisguiseManager.apply(context, id)
            },
            onDismiss = { showDisguise = false }
        )
    }

    if (showCalculatorCode) {
        AlertDialog(
            onDismissRequest = { showCalculatorCode = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Disguise passcode") },
            text = {
                Column {
                    Text(
                        "Type this code in the calculator and press =, or long-press the title in Notes or Calendar, to reveal Locker's lock screen. Your vault password is still required afterwards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    LockerTextField(
                        value = calculatorCode,
                        onValueChange = { calculatorCode = it.filter(Char::isDigit).take(8) },
                        placeholder = "4–8 digits",
                        label = "Passcode",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isSensitive = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (calculatorCode.length !in 4..8) {
                        android.widget.Toast.makeText(
                            context,
                            "Use 4–8 digits",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        app.preferences.setCalculatorCode(calculatorCode)
                        calculatorCode = ""
                        showCalculatorCode = false
                        android.widget.Toast.makeText(
                            context,
                            "Disguise passcode saved",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCalculatorCode = false }) { Text("Cancel") }
            }
        )
    }

    if (showDuress) {
        AlertDialog(
            onDismissRequest = { showDuress = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Set duress password") },
            text = {
                Column {
                    Text(
                        "Entering this password at the lock screen opens a separate decoy vault with harmless example entries. Your real vault is not erased, and your normal password still unlocks it. Resetting the duress password rebuilds the decoy's contents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    LockerTextField(
                        value = duressPassword,
                        onValueChange = { duressPassword = it },
                        placeholder = "At least 10 characters",
                        label = "Duress password",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        isSensitive = true
                    )
                    Spacer(Modifier.height(12.dp))
                    LockerTextField(
                        value = duressConfirm,
                        onValueChange = { duressConfirm = it },
                        placeholder = "Repeat password",
                        label = "Confirm",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        isSensitive = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        duressPassword.length < 10 -> android.widget.Toast.makeText(
                            context,
                            "Use at least 10 characters",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        duressPassword != duressConfirm -> android.widget.Toast.makeText(
                            context,
                            "Passwords don't match",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        else -> {
                            showDuress = false
                            val value = duressPassword
                            duressPassword = ""
                            duressConfirm = ""
                            scope.launch {
                                val result = app.setDuressPassword(value.toCharArray())
                                android.widget.Toast.makeText(
                                    context,
                                    result.fold(
                                        { "Duress password set — decoy vault ready" },
                                        { it.message ?: "Could not set duress password" }
                                    ),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }) { Text("Set password", color = BrandCoral) }
            },
            dismissButton = {
                TextButton(onClick = { showDuress = false }) { Text("Cancel") }
            }
        )
    }

    if (showWipeAttempts) {
        ChoiceDialog(
            title = "Erase after failed attempts",
            options = wipeOptions,
            selectedIndex = wipeValues.indexOf(settings.wipeAfterAttempts).coerceAtLeast(0),
            onSelect = { index -> app.preferences.setWipeAfterAttempts(wipeValues[index]) },
            onDismiss = { showWipeAttempts = false }
        )
    }

    if (showWipeVault) {
        WipeVaultDialog(onDismiss = { showWipeVault = false })
    }

    if (showRecoveryKey && recoveryKeyText != null) {
        RecoveryKeyDialog(
            code = recoveryKeyText.orEmpty(),
            onDismiss = { showRecoveryKey = false }
        )
    }
}

@Composable
private fun RecoveryKeyDialog(code: String, onDismiss: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val cleaned = RecoveryCodeFormat.normalize(code)
    val formatted = remember(code) { RecoveryCodeFormat.display(code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = BrandAmber) },
        title = { Text("Your recovery key", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "Keep this out of screenshots and cloud notes. Anyone with this key can unlock the vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        formatted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 0.6.sp,
                            lineHeight = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.secureClipboard.copy(
                    label = "Locker recovery key",
                    value = cleaned,
                    clearAfterSeconds = 120
                )
                android.widget.Toast.makeText(context, "Recovery key copied", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun autoLockLabel(seconds: Int): String = when (seconds) {
    0 -> "Immediately"
    30 -> "30 seconds"
    60 -> "1 minute"
    300 -> "5 minutes"
    900 -> "15 minutes"
    else -> "${seconds}s"
}

private fun clipboardLabel(seconds: Int): String = when (seconds) {
    0 -> "Never"
    15 -> "15 seconds"
    30 -> "30 seconds"
    60 -> "1 minute"
    120 -> "2 minutes"
    else -> "${seconds}s"
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val recoveryMode = settings.recoveryUsed
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val valid = (recoveryMode || current.isNotBlank()) &&
        newPassword.length >= 10 && newPassword == confirm

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                if (recoveryMode) "Set a new password" else "Change password",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (recoveryMode) {
                        "You recovered access with your recovery key. Set a new vault password now; your recovery key remains valid."
                    } else {
                        "Your items stay exactly the same. Only the key that wraps your vault changes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                if (!recoveryMode) {
                    LockerTextField(
                        value = current,
                        onValueChange = { current = it; error = null },
                        placeholder = "Current password",
                        label = "Current password",
                        required = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        isSensitive = true
                    )
                    Spacer(Modifier.height(14.dp))
                }
                LockerTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    placeholder = "At least 10 characters",
                    label = "New password",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    isSensitive = true,
                    trailingContent = {
                        androidx.compose.material3.IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle password visibility",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    placeholder = "Repeat new password",
                    label = "Confirm new password",
                    required = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    isSensitive = true
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        if (!recoveryMode) {
                            val master = runCatching { app.session.requireMasterKey() }.getOrNull()
                            if (master == null) {
                                loading = false
                                error = "The vault locked. Unlock it and try again."
                                return@launch
                            }
                            val verified = try {
                                withContext(Dispatchers.Default) {
                                    app.keyMaterialStore.verifyPassword(
                                        current.toCharArray(),
                                        master
                                    )
                                }
                            } finally {
                                Crypto.wipe(master)
                            }
                            if (!verified) {
                                loading = false
                                error = "The current password is incorrect."
                                return@launch
                            }
                        }
                        val result = app.session.changePassword(newPassword.toCharArray())
                        loading = false
                        when (result) {
                            is UnlockResult.Success -> {
                                android.widget.Toast.makeText(
                                    context,
                                    "Password changed",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                            is UnlockResult.Failure -> {
                                error = result.message
                            }
                            else -> error = "Could not change password"
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).padding(horizontal = 10.dp),
                    strokeWidth = 2.dp
                ) else Text("Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun WipeVaultDialog(onDismiss: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf("") }
    var wiping by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!wiping) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                "Erase this vault?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    "Every encrypted item, file, collection, and key will be destroyed immediately. There is no backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    placeholder = "Type ERASE to confirm",
                    label = "Confirmation"
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmation.trim() == "ERASE" && !wiping,
                onClick = {
                    wiping = true
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            "Vault erased",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        app.wipeVault()
                    }
                }
            ) {
                Text("Erase everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !wiping) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
