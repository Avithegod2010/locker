package app.locker.vault.ui.screens.settings

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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locker.vault.core.RecoveryShares
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint

/**
 * Splits the numeric recovery code into three paper shares with a 2-of-3 threshold.
 * Reconstruction is local and stateless; shares are never written to preferences.
 */
@Composable
fun RecoverySharesScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    SecureWindow()
    var shares by remember { mutableStateOf<List<String>>(emptyList()) }
    var createError by remember { mutableStateOf<String?>(null) }
    var input1 by remember { mutableStateOf("") }
    var input2 by remember { mutableStateOf("") }
    var input3 by remember { mutableStateOf("") }
    var combined by remember { mutableStateOf<String?>(null) }
    var combineError by remember { mutableStateOf<String?>(null) }

    fun createShares() {
        val code = app.session.recoveryCode()
        if (code == null) {
            createError = "Unlock the vault to read the recovery code."
            return
        }
        runCatching { RecoveryShares.split(code) }
            .onSuccess { shares = it; createError = null }
            .onFailure { createError = it.message ?: "Could not create shares" }
    }

    fun combine() {
        val result = RecoveryShares.combine(listOf(input1, input2, input3))
        if (result == null) {
            combined = null
            combineError = "Those shares could not be combined. Check that you entered at least two complete shares."
        } else {
            combined = result
            combineError = null
        }
    }

    SettingsScaffold(title = "Recovery shares", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            icon = Icons.Rounded.Groups,
            tint = BrandBlue,
            title = "2 of 3 shares recover the key",
            text = "Split the numeric recovery code into three paper shares. Any two reconstruct it; one share reveals nothing. Keep each share in a different private place."
        )

        SettingsSectionTitle("Create shares")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Key,
                tint = BrandMint,
                title = "Split my recovery key",
                subtitle = "Generates three new shares. Existing shares are not stored by Locker.",
                onClick = { createShares() }
            )
        }
        if (createError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                createError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
        if (shares.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            shares.forEachIndexed { index, share ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "SHARE ${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                app.secureClipboard.copy(
                                    "Locker recovery share ${index + 1}",
                                    share,
                                    app.preferences.settings.value.clipboardClearSeconds
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    "Share ${index + 1} copied",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy share ${index + 1}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Text(
                            share,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BrandAmber.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = BrandAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Store the shares separately. A photo of all three is the same as a photo of the recovery key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSectionTitle("Combine shares")
        LockerTextField(
            value = input1,
            onValueChange = { input1 = it; combineError = null },
            placeholder = "LKR1-1-…",
            label = "Share 1"
        )
        Spacer(Modifier.height(10.dp))
        LockerTextField(
            value = input2,
            onValueChange = { input2 = it; combineError = null },
            placeholder = "LKR1-2-…",
            label = "Share 2"
        )
        Spacer(Modifier.height(10.dp))
        LockerTextField(
            value = input3,
            onValueChange = { input3 = it; combineError = null },
            placeholder = "Optional third share",
            label = "Share 3"
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { combine() }) {
                Text("Reconstruct recovery key", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (combined != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BrandMint.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    combined ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (combineError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                combineError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(36.dp))
    }
}
