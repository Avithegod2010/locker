package app.locker.vault.ui.screens.editor

import android.app.DatePickerDialog
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
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.locker.vault.core.Formatting
import app.locker.vault.core.PasswordGenerator
import app.locker.vault.core.Totp
import app.locker.vault.data.CustomField
import app.locker.vault.data.ItemTemplate
import app.locker.vault.data.ItemTemplates
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.FilterPill
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.accent
import app.locker.vault.ui.screens.onboarding.scorePassword
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TextEditorScreen(
    type: VaultType,
    itemId: String?,
    onBack: () -> Unit
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val allItems by app.repository.items.collectAsStateWithLifecycle()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val existing = remember(itemId, allItems) {
        itemId?.let { id -> allItems.firstOrNull { it.id == id } }
    }
    val effectiveType = existing?.type ?: type

    if (itemId != null && existing == null) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    var title by remember(itemId, effectiveType) {
        mutableStateOf(existing?.title.orEmpty())
    }
    var body by remember(itemId, effectiveType) {
        mutableStateOf(existing?.body.orEmpty())
    }
    var username by remember(itemId, effectiveType) {
        mutableStateOf(existing?.username.orEmpty())
    }
    var password by remember(itemId, effectiveType) {
        mutableStateOf(existing?.password.orEmpty())
    }
    var location by remember(itemId, effectiveType) {
        mutableStateOf(existing?.location.orEmpty())
    }
    var website by remember(itemId, effectiveType) {
        mutableStateOf(existing?.website.orEmpty())
    }
    var otpSecret by remember(itemId, effectiveType) {
        mutableStateOf(existing?.otpSecret.orEmpty())
    }
    var tags by remember(itemId, effectiveType) {
        mutableStateOf(existing?.tags ?: emptyList())
    }
    var tagInput by remember { mutableStateOf("") }
    var customFields by remember(itemId, effectiveType) {
        mutableStateOf(existing?.fields ?: emptyList())
    }
    var favorite by rememberSaveable(itemId, effectiveType) {
        mutableStateOf(existing?.favorite ?: false)
    }
    var selectedCollections by remember(itemId, effectiveType) {
        mutableStateOf(existing?.collectionIds ?: emptyList())
    }
    var expiresAt by remember(itemId, effectiveType) {
        mutableLongStateOf(existing?.expiresAt ?: 0L)
    }
    var requireBiometric by remember(itemId, effectiveType) {
        mutableStateOf(existing?.requireBiometric ?: false)
    }
    val biometricAvailable = remember { app.biometricKeyStore.canAuthenticate() }
    var revealPassword by rememberSaveable(itemId, effectiveType) { mutableStateOf(false) }
    var showGeneratorOptions by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val requiredSatisfied = when (effectiveType) {
        VaultType.SECRET -> title.isNotBlank()
        VaultType.NOTE -> body.isNotBlank()
        VaultType.THING -> title.isNotBlank()
        VaultType.FILE, VaultType.PHOTO, VaultType.VIDEO -> title.isNotBlank()
    }

    fun save() {
        if (!requiredSatisfied || saving) return
        saving = true
        error = null
        scope.launch {
            val result = if (existing == null) {
                app.repository.createTextItem(
                    type = effectiveType,
                    title = title,
                    body = body,
                    username = username,
                    password = password,
                    location = location,
                    website = website,
                    otpSecret = otpSecret,
                    tags = tags,
                    fields = customFields,
                    collectionIds = selectedCollections.toList(),
                    favorite = favorite,
                    expiresAt = expiresAt,
                    requireBiometric = requireBiometric
                )
            } else {
                app.repository.updateItem(
                    existing.copy(
                        title = title,
                        body = body,
                        username = username,
                        password = password,
                        location = location,
                        website = website,
                        otpSecret = otpSecret,
                        tags = tags,
                        fields = customFields,
                        favorite = favorite,
                        collectionIds = selectedCollections.toList(),
                        expiresAt = expiresAt,
                        requireBiometric = requireBiometric
                    )
                )
            }
            saving = false
            result.onSuccess { onBack() }
                .onFailure { error = it.message ?: "Could not save this item" }
        }
    }

    val titleOfScreen = when {
        existing != null -> "Edit ${effectiveType.label.lowercase()}"
        effectiveType == VaultType.SECRET -> "Secret"
        effectiveType == VaultType.NOTE -> "Note"
        effectiveType == VaultType.THING -> "Thing"
        else -> effectiveType.label
    }

    EditorScaffold(
        title = titleOfScreen,
        onBack = onBack,
        saveEnabled = requiredSatisfied,
        saving = saving,
        onSave = { save() }
    ) {
        Spacer(Modifier.height(14.dp))

        if (existing == null &&
            (effectiveType == VaultType.SECRET || effectiveType == VaultType.NOTE)
        ) {
            ItemTemplateRow(
                type = effectiveType,
                onPick = { template ->
                    if (template.title.isNotBlank()) title = template.title
                    if (template.body.isNotBlank()) body = template.body
                    if (template.username.isNotBlank()) username = template.username
                    if (template.password.isNotBlank()) password = template.password
                    if (template.website.isNotBlank()) website = template.website
                    if (template.location.isNotBlank()) location = template.location
                    if (template.fields.isNotEmpty()) customFields = template.fields
                }
            )
            Spacer(Modifier.height(22.dp))
        }

        if (effectiveType == VaultType.FILE || effectiveType == VaultType.PHOTO || effectiveType == VaultType.VIDEO) {
            if (existing != null) {
                FileSummaryCard(item = existing)
                Spacer(Modifier.height(20.dp))
            }
            LockerTextField(
                value = title,
                onValueChange = { title = it; error = null },
                placeholder = when (effectiveType) {
                    VaultType.PHOTO -> "Photo title"
                    VaultType.VIDEO -> "Video title"
                    else -> "File title"
                },
                label = "Title",
                required = true
            )
        }

        if (effectiveType == VaultType.SECRET) {
            LockerTextField(
                value = title,
                onValueChange = { title = it; error = null },
                placeholder = "Name of the service",
                label = "Account",
                required = true
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Login ID or email",
                label = "Username",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = website,
                onValueChange = { website = it },
                placeholder = "example.com or app package",
                label = "Website or app",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = "Saved encrypted; also used for autofill matching."
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                label = "Password",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (revealPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                isSensitive = true,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                password = PasswordGenerator.generate(settings)
                                revealPassword = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "Generate password",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = { showGeneratorOptions = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Generator options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = { revealPassword = !revealPassword },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (revealPassword) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            )
            if (password.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                PasswordStrengthInline(password)
            }
            if (showGeneratorOptions && effectiveType == VaultType.SECRET) {
                AlertDialog(
                    onDismissRequest = { showGeneratorOptions = false },
                    title = {
                        Text(
                            "Password generator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Length · ${settings.generatorLength}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = settings.generatorLength.toFloat(),
                                onValueChange = { app.preferences.setGeneratorLength(it.roundToInt()) },
                                valueRange = 10f..64f,
                                steps = 53
                            )
                            Spacer(Modifier.height(4.dp))
                            GeneratorSwitch("Include digits", settings.generatorDigits) {
                                app.preferences.setGeneratorDigits(it)
                            }
                            GeneratorSwitch("Include symbols", settings.generatorSymbols) {
                                app.preferences.setGeneratorSymbols(it)
                            }
                            GeneratorSwitch(
                                "Avoid look-alike characters (l I O 0 1)",
                                settings.generatorNoAmbiguous
                            ) {
                                app.preferences.setGeneratorNoAmbiguous(it)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            password = PasswordGenerator.generate(settings)
                            revealPassword = true
                            showGeneratorOptions = false
                        }) { Text("Generate") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGeneratorOptions = false }) { Text("Done") }
                    }
                )
            }
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = otpSecret,
                onValueChange = { otpSecret = it },
                placeholder = "otpauth://totp/... or setup key",
                label = "One-time password (TOTP)",
                isSensitive = true,
                supportingText = when {
                    otpSecret.isBlank() -> "Optional. Paste a setup key or otpauth:// URI."
                    Totp.parse(otpSecret) != null -> "Valid TOTP secret. A live code will appear in the item."
                    else -> "That doesn't look like a valid TOTP secret yet."
                }
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = "Recovery methods, security questions, …",
                label = "Additional notes",
                minLines = 4,
                maxLines = 9
            )
        }

        if (effectiveType == VaultType.NOTE) {
            LockerTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Note title",
                label = "Title"
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = body,
                onValueChange = { body = it; error = null },
                placeholder = "Write down important thoughts or instructions",
                label = "Content",
                required = true,
                minLines = 8,
                maxLines = 20
            )
        }

        if (effectiveType == VaultType.THING) {
            if (existing == null) {
                ThingTemplateRow(
                    onPick = { template ->
                        title = template.title
                        location = template.location
                        body = template.notes
                        customFields = template.fields
                    }
                )
                Spacer(Modifier.height(18.dp))
            }
            LockerTextField(
                value = title,
                onValueChange = { title = it; error = null },
                placeholder = "Name of the real-world item",
                label = "Name",
                required = true
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = "Where is this item kept?",
                label = "Location"
            )
            Spacer(Modifier.height(18.dp))
            LockerTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = "Additional information about the item",
                label = "Notes",
                minLines = 4,
                maxLines = 10
            )
        }

        if (effectiveType == VaultType.SECRET || effectiveType == VaultType.THING) {
            Spacer(Modifier.height(22.dp))
            ExpiryPicker(
                value = expiresAt,
                onChange = { expiresAt = it },
                onClear = { expiresAt = 0L }
            )
        }

        Spacer(Modifier.height(22.dp))
        CollectionsSelector(
            collections = collections,
            selectedIds = selectedCollections,
            onToggle = { id ->
                selectedCollections = if (id in selectedCollections) {
                    selectedCollections - id
                } else {
                    selectedCollections + id
                }
            },
            onCreated = { id -> selectedCollections = selectedCollections + id }
        )

        Spacer(Modifier.height(22.dp))
        TagEditor(
            tags = tags,
            input = tagInput,
            onInput = { tagInput = it },
            onAdd = {
                val value = tagInput.trim().removePrefix("#")
                if (value.isNotBlank()) {
                    tags = (tags + value).distinctBy { it.lowercase() }
                    tagInput = ""
                }
            },
            onRemove = { tag -> tags = tags - tag }
        )

        Spacer(Modifier.height(22.dp))
        CustomFieldsEditor(
            fields = customFields,
            onChange = { customFields = it }
        )

        Spacer(Modifier.height(20.dp))
        ImportantToggle(
            checked = favorite,
            onCheckedChange = { favorite = it }
        )

        Spacer(Modifier.height(16.dp))
        BiometricGateToggle(
            checked = requireBiometric,
            available = biometricAvailable,
            onCheckedChange = { requireBiometric = it }
        )

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Encrypted with AES-256-GCM before saving",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ExpiryPicker(
    value: Long,
    onChange: (Long) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    fun openPicker() {
        val calendar = Calendar.getInstance()
        if (value > 0L) calendar.timeInMillis = value
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                onChange(picked.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Surface(
        onClick = { openPicker() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "EXPIRY DATE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (value > 0L) "Expires ${Formatting.dateOnly(value)}" else "No expiry date",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Optional. Passport, insurance, cards, and renewals appear on the health dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            if (value > 0L) {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    "Set",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FileSummaryCard(item: VaultItem) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = if (item.type == VaultType.PHOTO) Icons.Rounded.Image else Icons.Rounded.Description,
                tint = item.type.accent(),
                size = 48.dp
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.fileName.ifBlank { item.title },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Formatting.bytes(item.sizeBytes)} · ${item.mimeType.ifBlank { "Encrypted file" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private data class ThingTemplate(
    val name: String,
    val title: String,
    val location: String,
    val notes: String,
    val fields: List<CustomField>
)

private val thingTemplates = listOf(
    ThingTemplate(
        "Passport",
        "Passport",
        "Travel folder",
        "Keep the physical document in a safe place.",
        listOf(
            CustomField("Passport number", ""),
            CustomField("Issued", ""),
            CustomField("Expires", "")
        )
    ),
    ThingTemplate(
        "Insurance",
        "Insurance policy",
        "Documents",
        "Policy details and renewal date.",
        listOf(
            CustomField("Provider", ""),
            CustomField("Policy number", ""),
            CustomField("Renews", "")
        )
    ),
    ThingTemplate(
        "Vehicle",
        "Vehicle",
        "Garage",
        "Registration, insurance, and service details.",
        listOf(
            CustomField("Registration", ""),
            CustomField("Insurance", ""),
            CustomField("Service due", "")
        )
    ),
    ThingTemplate(
        "Home Wi-Fi",
        "Home Wi-Fi",
        "Router",
        "Network name and password.",
        listOf(
            CustomField("Network", ""),
            CustomField("Password", "", sensitive = true),
            CustomField("Router location", "")
        )
    ),
    ThingTemplate(
        "Subscription",
        "Subscription",
        "Billing",
        "Renewal and payment details.",
        listOf(
            CustomField("Plan", ""),
            CustomField("Renews", ""),
            CustomField("Cost", "")
        )
    ),
    ThingTemplate(
        "Loan",
        "Loan",
        "People",
        "Who has it, when it is due, and what it is worth.",
        listOf(
            CustomField("Lent to", ""),
            CustomField("Due", ""),
            CustomField("Amount", "")
        )
    ),
    ThingTemplate(
        "Medical",
        "Medical information",
        "Emergency",
        "Allergies, medications, and emergency contacts.",
        listOf(
            CustomField("Allergies", ""),
            CustomField("Medications", ""),
            CustomField("Doctor", "")
        )
    ),
    ThingTemplate(
        "Recipe",
        "Family recipe",
        "Recipes",
        "Ingredients and method.",
        listOf(CustomField("Serves", ""), CustomField("Ingredients", ""))
    )
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ItemTemplateRow(type: VaultType, onPick: (ItemTemplate) -> Unit) {
    val templates = ItemTemplates.forType(type)
    if (templates.isEmpty()) return
    Column {
        Text(
            if (type == VaultType.NOTE) "NOTE TEMPLATE" else "QUICK TEMPLATE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            templates.forEach { template ->
                FilterPill(
                    label = template.name,
                    selected = false,
                    onClick = { onPick(template) }
                )
            }
        }
    }
}

@Composable
private fun BiometricGateToggle(
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = if (available) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Require biometrics",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (available) {
                        "Ask for a fingerprint before showing or copying this item's contents."
                    } else {
                        "No strong biometric is enrolled on this device yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = available
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ThingTemplateRow(onPick: (ThingTemplate) -> Unit) {
    Column {
        Text(
            "QUICK TEMPLATE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            thingTemplates.forEach { template ->
                FilterPill(
                    label = template.name,
                    selected = false,
                    onClick = { onPick(template) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TagEditor(
    tags: List<String>,
    input: String,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        LockerTextField(
            value = input,
            onValueChange = onInput,
            placeholder = "Add a tag",
            label = "Tags",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            trailingContent = {
                IconButton(onClick = onAdd, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add tag",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            },
            supportingText = "Tags cut across collections. Use them for projects, people, or trips."
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { onRemove(tag) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove tag",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFieldsEditor(
    fields: List<CustomField>,
    onChange: (List<CustomField>) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CUSTOM FIELDS",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onChange(fields + CustomField("", "")) }) {
                Text("Add field", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (fields.isEmpty()) {
            Text(
                "Optional: add labels like PIN, account number, recovery email, or serial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        fields.forEachIndexed { index, field ->
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "FIELD ${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onChange(fields.filterIndexed { i, _ -> i != index }) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove field",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    LockerTextField(
                        value = field.label,
                        onValueChange = { value ->
                            onChange(fields.toMutableList().also { it[index] = field.copy(label = value) })
                        },
                        placeholder = "Label",
                        label = null
                    )
                    Spacer(Modifier.height(8.dp))
                    LockerTextField(
                        value = field.value,
                        onValueChange = { value ->
                            onChange(fields.toMutableList().also { it[index] = field.copy(value = value) })
                        },
                        placeholder = "Value",
                        label = null
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordStrengthInline(password: String) {
    val score = scorePassword(password)
    val color = when (score) {
        1 -> MaterialTheme.colorScheme.error
        2 -> BrandAmber
        3 -> BrandAmber
        else -> BrandMint
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .padding(end = if (index < 3) 4.dp else 0.dp)
                    .background(
                        if (index < score) color
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(4.dp)
                    )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when (score) {
                1 -> "Weak"
                2 -> "Fair"
                3 -> "Strong"
                else -> "Excellent"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun GeneratorSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
