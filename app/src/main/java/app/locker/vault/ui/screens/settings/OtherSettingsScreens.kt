package app.locker.vault.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.BuildConfig
import app.locker.vault.R
import app.locker.vault.core.Formatting
import app.locker.vault.core.SortMode
import app.locker.vault.core.ThemeMode
import app.locker.vault.data.VaultProfile
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.FilterPill
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.PrimaryButton
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.launch

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsScaffold(title = "Appearance", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ThemePreviewCard(
                label = "System",
                mode = ThemeMode.SYSTEM,
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onClick = { app.preferences.setThemeMode(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemePreviewCard(
                label = "Light",
                mode = ThemeMode.LIGHT,
                selected = settings.themeMode == ThemeMode.LIGHT,
                onClick = { app.preferences.setThemeMode(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemePreviewCard(
                label = "Dark",
                mode = ThemeMode.DARK,
                selected = settings.themeMode == ThemeMode.DARK,
                onClick = { app.preferences.setThemeMode(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }

        SettingsSectionTitle("Material You")
        SettingsGroup {
            SettingSwitchRow(
                icon = Icons.Rounded.Palette,
                tint = BrandViolet,
                title = "Use wallpaper colors",
                subtitle = if (dynamicSupported) {
                    "Let Android Material You tint Locker from your wallpaper."
                } else {
                    "Dynamic color requires Android 12 or newer."
                },
                checked = settings.dynamicColor && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = { app.preferences.setDynamicColor(it) }
            )
        }
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            icon = Icons.Rounded.Info,
            tint = BrandBlue,
            title = "Material You, with a Locker accent",
            text = if (settings.dynamicColor && dynamicSupported) {
                "Locker is using colors derived from your wallpaper. The rest of the interface still follows Material 3 shapes, motion, and type."
            } else {
                "Locker uses its own deep blue palette on top of Material 3 components. Turn on wallpaper colors for a fully personal theme."
            }
        )

        SettingsSectionTitle("Feedback")
        SettingsGroup {
            SettingSwitchRow(
                icon = Icons.Rounded.Vibration,
                tint = BrandMint,
                title = "Haptic feedback",
                subtitle = "A subtle vibration when you tap cards, chips, and primary actions.",
                checked = settings.haptics,
                onCheckedChange = { app.preferences.setHaptics(it) }
            )
        }

        SettingsSectionTitle("Preview")
        LockerCard(
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shield_heart),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Locker",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Private by default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Important", selected = true, onClick = {})
                FilterPill("Documents", selected = false, onClick = {})
            }
        }

        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun ThemePreviewCard(
    label: String,
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF0B0F12)
    val lightBg = Color(0xFFF2F5F9)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(118.dp)
            .then(
                if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(22.dp))
                else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(22.dp)
                )
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (mode) {
                            ThemeMode.DARK -> Brush.linearGradient(listOf(darkBg, darkBg))
                            ThemeMode.LIGHT -> Brush.linearGradient(listOf(lightBg, lightBg))
                            ThemeMode.SYSTEM -> Brush.linearGradient(listOf(darkBg, lightBg))
                        }
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Box(
                        Modifier
                            .width(30.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (mode == ThemeMode.LIGHT) Color(0xFF263238)
                                else Color.White.copy(alpha = 0.85f)
                            )
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.75f))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selected) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val legacy by app.repository.legacyContacts.collectAsStateWithLifecycle()
    val active = items.filter { it.deletedAt == null }
    val bytes = active.sumOf { it.sizeBytes }

    SettingsScaffold(title = "General", onBack = onBack) {
        Spacer(Modifier.height(6.dp))
        InfoBanner(
            icon = Icons.Rounded.Storage,
            tint = BrandMint,
            title = "Everything lives here",
            text = "${active.size} items · ${Formatting.bytes(bytes)} · ${collections.size} collections · ${legacy.size} legacy contacts. All encrypted on this device."
        )

        SettingsSectionTitle("Sorting")
        SettingsGroup {
            listOf(
                Triple(SortMode.NEWEST, "Newest first", "Recently edited items appear first"),
                Triple(SortMode.OLDEST, "Oldest first", "Original items appear first"),
                Triple(SortMode.NAME, "Name A–Z", "Alphabetical by title")
            ).forEachIndexed { index, (mode, label, description) ->
                if (index > 0) RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Settings,
                    tint = BrandBlue,
                    title = label,
                    subtitle = description,
                    trailing = {
                        if (settings.sortMode == mode) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    onClick = { app.preferences.setSortMode(mode) }
                )
            }
        }

        SettingsSectionTitle("Storage")
        SettingsGroup {
            SettingSwitchRow(
                icon = Icons.Rounded.Storage,
                tint = BrandAmber,
                title = "Auto-delete trash",
                subtitle = "Permanently erase trashed items after 30 days.",
                checked = settings.autoPurgeTrash,
                onCheckedChange = { app.preferences.setAutoPurgeTrash(it) }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Shield,
                tint = BrandMint,
                title = "Screenshot protection",
                subtitle = "Managed from Security settings.",
                value = if (settings.blockScreenshots) "On" else "Off"
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Lock,
                tint = BrandViolet,
                title = "Auto-lock",
                subtitle = "Managed from Security settings.",
                value = when (settings.autoLockSeconds) {
                    0 -> "Immediate"
                    30 -> "30s"
                    60 -> "1m"
                    300 -> "5m"
                    900 -> "15m"
                    else -> "${settings.autoLockSeconds}s"
                }
            )
        }

        SettingsSectionTitle("About this build")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Info,
                tint = BrandViolet,
                title = "Version",
                value = "${BuildConfig.VERSION_NAME}${if (BuildConfig.DEV_BUILD) " (dev)" else ""}"
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Security,
                tint = BrandMint,
                title = "Network access",
                subtitle = "Locker has no INTERNET permission.",
                value = "None"
            )
        }

        Spacer(Modifier.height(36.dp))
    }
}

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val profile by app.repository.profile.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val legacy by app.repository.legacyContacts.collectAsStateWithLifecycle()
    val active = items.filter { it.deletedAt == null }
    var showEdit by remember { mutableStateOf(false) }
    val initial = profile.name.trim().take(1).uppercase().ifBlank { "L" }

    SettingsScaffold(title = "Account", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        LockerCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(15.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        profile.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { showEdit = true }) { Text("Edit") }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = BrandMint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Local profile · encrypted inside your vault",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSectionTitle("Your vault")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatTile(
                value = active.size.toString(),
                label = "items",
                tint = BrandBlue,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = collections.size.toString(),
                label = "collections",
                tint = BrandMint,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = Formatting.bytes(active.sumOf { it.sizeBytes }),
                label = "encrypted",
                tint = BrandViolet,
                modifier = Modifier.weight(1f)
            )
        }

        SettingsSectionTitle("Privacy")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Person,
                tint = BrandBlue,
                title = "Profile",
                subtitle = "Display name and label for this local vault.",
                onClick = { showEdit = true }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Fingerprint,
                tint = BrandViolet,
                title = "Biometrics",
                subtitle = "Managed from Security settings."
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Lock,
                tint = BrandAmber,
                title = "Lock vault now",
                onClick = { app.lockNow() }
            )
        }

        SettingsSectionTitle("Emergency")
        InfoBanner(
            icon = Icons.Rounded.Shield,
            tint = BrandAmber,
            title = "${legacy.size} legacy contacts saved",
            text = "Your legacy contacts and notes are encrypted like everything else. Nothing is sent to them automatically."
        )
        Spacer(Modifier.height(36.dp))
    }

    if (showEdit) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEdit = false }
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 15.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditProfileDialog(profile: VaultProfile, onDismiss: () -> Unit) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(profile.name) }
    var email by remember { mutableStateOf(profile.email) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Edit local profile", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "These details are encrypted inside your vault, not stored in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                LockerTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    placeholder = "Your name",
                    label = "Name"
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    placeholder = "you@example.com",
                    label = "Label or email"
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
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        app.repository.saveProfile(name, email)
                            .onSuccess { onDismiss() }
                            .onFailure {
                                saving = false
                                error = "Could not save profile"
                            }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "About", onBack = onBack) {
        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shield_heart),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Locker",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEV_BUILD) " · dev build" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "A calm, private home for your passwords, notes, documents, things, and photos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecurityPill("No internet")
                SecurityPill("No account")
                SecurityPill("No telemetry")
                SecurityPill("Local only")
            }
        }

        SettingsSectionTitle("How Locker protects you")
        SettingsGroup {
            AboutSecurityRow("AES-256-GCM", "Authenticated encryption for every item and file chunk.")
            RowDivider()
            AboutSecurityRow("Argon2id", "Memory-hard key derivation with 64 MB, 3 passes, and 4 lanes.")
            RowDivider()
            AboutSecurityRow("Per-file keys", "Every file has a unique random key wrapped by the vault key.")
            RowDivider()
            AboutSecurityRow("Android Keystore", "Biometric keys are hardware-backed and authentication-bound.")
            RowDivider()
            AboutSecurityRow("Encrypted profile", "Even your display name is encrypted inside the vault.")
            RowDivider()
            AboutSecurityRow("No network permission", "The APK cannot create an internet connection.")
        }

        SettingsSectionTitle("Built with")
        InfoBanner(
            icon = Icons.Rounded.Info,
            tint = BrandBlue,
            title = "Open, dependable pieces",
            text = "Kotlin, Jetpack Compose, Material 3, Bouncy Castle Argon2, and the Plus Jakarta Sans typeface under the SIL Open Font License."
        )

        Spacer(Modifier.height(18.dp))
        Text(
            "Your vault belongs to you. No servers, no resets, no tracking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(38.dp))
    }
}

@Composable
private fun SecurityPill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = BrandMint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSecurityRow(title: String, subtitle: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(
            Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = BrandMint,
            modifier = Modifier.padding(top = 1.dp).size(19.dp)
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}
