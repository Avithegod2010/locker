package app.locker.vault.ui.screens.disguise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DisguisePasscodeDialog(
    title: String = "Enter passcode",
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val app = LocalAppContainer.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Enter the app passcode to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(8)
                        error = false
                    },
                    placeholder = "4–8 digits",
                    label = "Passcode",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isSensitive = true,
                    supportingText = if (error) "Incorrect passcode" else ""
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code.isNotBlank() && app.preferences.calculatorCodeMatches(code)) {
                    onUnlocked()
                } else {
                    error = true
                }
            }) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
