package app.locker.vault.ui.screens.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.BuildConfig
import app.locker.vault.core.CrashReporter
import app.locker.vault.core.Formatting
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local-only diagnostics: build information and the app-private crash log. There is no
 * upload path, no analytics, and no network permission.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    SecureWindow()
    var log by remember { mutableStateOf(CrashReporter.read(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    fun buildReport(): String = buildString {
        appendLine("Locker diagnostics")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEV_BUILD) " dev" else ""}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Items: ${items.count { it.deletedAt == null }} · Collections: ${collections.size}")
        appendLine("Encrypted bytes: ${Formatting.bytes(items.filter { it.deletedAt == null }.sumOf { it.sizeBytes })}")
        appendLine("Network permission: none")
        appendLine()
        appendLine("Crash log")
        appendLine(if (log.isBlank()) "No crash reports recorded." else log)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        app.session.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(buildReport().toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            message = if (ok) "Diagnostics exported" else "Could not export diagnostics"
        }
    }

    SettingsScaffold(title = "Diagnostics", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            icon = Icons.Rounded.Info,
            tint = BrandBlue,
            title = "Stays on this device",
            text = "Locker records uncaught crashes in an app-private file. Exporting creates the text file you choose through the system picker; nothing is uploaded."
        )

        SettingsSectionTitle("Build")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Info,
                tint = BrandViolet,
                title = "Version",
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.BugReport,
                tint = BrandBlue,
                title = "Android",
                value = "${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}"
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.BugReport,
                tint = BrandMint,
                title = "Device",
                value = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        }

        SettingsSectionTitle("Crash reports")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.FileDownload,
                tint = BrandMint,
                title = "Export diagnostics",
                subtitle = "Build information plus the crash log as a plain text file.",
                value = "Text",
                onClick = {
                    app.session.beginExternalActivity()
                    exportLauncher.launch("Locker-diagnostics.txt")
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.DeleteSweep,
                tint = BrandAmber,
                title = "Clear crash log",
                subtitle = "Removes every recorded crash from this device.",
                onClick = {
                    CrashReporter.clear(context)
                    log = ""
                    message = "Crash log cleared"
                }
            )
        }

        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                message ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        if (log.isBlank()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No crash reports recorded. That's a good thing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        log.takeLast(20_000),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}
