package app.locker.vault.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.locker.vault.BiometricAuthActivity
import app.locker.vault.ui.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Prompt shown before a locked collection reveals its contents. Biometrics are preferred when
 * the device has a strong sensor enrolled; the vault password is always accepted as a fallback so
 * a device without biometrics (or with a temporarily unavailable sensor) can never lock the owner
 * out of their own items.
 */
@Composable
fun CollectionUnlockDialog(
    collectionName: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val app = LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val biometricsAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val biometricLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        } else {
            error = "Biometric confirmation was cancelled"
        }
    }

    fun launchBiometrics() {
        val intent = android.content.Intent(context, BiometricAuthActivity::class.java)
            .putExtra(BiometricAuthActivity.EXTRA_MODE, BiometricAuthActivity.MODE_CONFIRM)
            .putExtra(
                BiometricAuthActivity.EXTRA_TITLE,
                "Unlock “$collectionName”"
            )
            .putExtra(
                BiometricAuthActivity.EXTRA_SUBTITLE,
                "This collection stays sealed until you confirm"
            )
        biometricLauncher.launch(intent)
    }

    LaunchedEffect(Unit) {
        if (biometricsAvailable) launchBiometrics()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text("“$collectionName” is locked") },
        text = {
            Column {
                Text(
                    "Enter your vault password, or use biometrics, to see this collection. " +
                        "It stays unlocked until Locker locks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    placeholder = "Vault password",
                    label = "Password",
                    isSensitive = true
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
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
                enabled = password.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    val candidate = password
                    password = ""
                    scope.launch {
                        val ok = runCatching {
                            app.session.verifyPasswordQuiet(candidate.toCharArray())
                        }.getOrDefault(false)
                        busy = false
                        if (ok) onUnlocked() else error = "That password did not unlock the vault"
                    }
                }
            ) { Text("Unlock") }
        },
        dismissButton = {
            Column(Modifier.padding(end = 8.dp)) {
                if (biometricsAvailable) {
                    TextButton(onClick = { error = null; launchBiometrics() }, enabled = !busy) {
                        Text("Use biometrics")
                    }
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}
