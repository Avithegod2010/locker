package app.locker.vault.ui.screens.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locker.vault.data.VaultCollection
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.DashedAddChip
import app.locker.vault.ui.components.FilterPill
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.PrimaryButton
import app.locker.vault.ui.components.collectionColors
import app.locker.vault.ui.components.collectionIcons
import kotlinx.coroutines.launch

@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    saveEnabled: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    saveLabel: String = "Save",
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = saving) {
        // A save/import is in progress; don't dispose the coroutine and lose the input.
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !saving) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (saving) 0.4f else 1f),
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                actions()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
                content = content
            )

            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    PrimaryButton(
                        text = saveLabel,
                        onClick = onSave,
                        enabled = saveEnabled,
                        loading = saving
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionsSelector(
    collections: List<VaultCollection>,
    selectedIds: Collection<String>,
    onToggle: (String) -> Unit,
    onCreated: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }

    Column {
        Text(
            "Collections",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(11.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashedAddChip(label = "Collection", onClick = { showCreate = true })
            collections.forEach { collection ->
                FilterPill(
                    label = collection.name,
                    selected = collection.id in selectedIds,
                    onClick = { onToggle(collection.id) },
                    leadingIcon = if (collection.id in selectedIds) Icons.Rounded.Check else Icons.Rounded.Folder
                )
            }
        }
    }

    if (showCreate) {
        NewCollectionDialog(
            onDismiss = { showCreate = false },
            onCreated = { newId ->
                showCreate = false
                onCreated(newId)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCollectionDialog(
    existing: VaultCollection? = null,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember {
        mutableStateOf(existing?.let { Color(it.color) } ?: collectionColors.first())
    }
    var icon by remember { mutableStateOf(existing?.icon ?: "folder") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                if (existing == null) "New collection" else "Edit collection",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                LockerTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    placeholder = "Collection name",
                    label = "Name",
                    required = true
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "COLOR",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    collectionColors.forEach { candidate ->
                        Surface(
                            onClick = { color = candidate },
                            shape = CircleShape,
                            color = candidate,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (candidate == color) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected color",
                                        tint = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "ICON",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collectionIcons.forEach { option ->
                        Surface(
                            onClick = { icon = option.key },
                            shape = RoundedCornerShape(13.dp),
                            color = if (icon == option.key) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    option.icon,
                                    contentDescription = option.key,
                                    tint = if (icon == option.key) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
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
                enabled = name.isNotBlank() && !loading,
                onClick = {
                    val trimmed = name.trim()
                    val duplicate = app.repository.collections.value.any {
                        it.id != existing?.id && it.name.equals(trimmed, ignoreCase = true)
                    }
                    if (trimmed.equals("Important", ignoreCase = true)) {
                        error = "\"Important\" is reserved for your starred items."
                        return@TextButton
                    }
                    if (duplicate) {
                        error = "A collection with that name already exists."
                        return@TextButton
                    }
                    loading = true
                    scope.launch {
                        val colorLong = color.toArgb().toLong() and 0xFFFFFFFFL
                        val result = if (existing == null) {
                            app.repository.addCollection(name, colorLong, icon)
                                .map { it.id }
                        } else {
                            app.repository.updateCollection(
                                existing.copy(name = name.trim(), color = colorLong, icon = icon)
                            ).map { existing.id }
                        }
                        result
                            .onSuccess {
                                loading = false
                                onCreated(it)
                            }
                            .onFailure {
                                loading = false
                                error = "Could not save collection"
                            }
                    }
                }
            ) {
                Text(if (existing == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImportantToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mark as important",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Pin it to the Important filter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
