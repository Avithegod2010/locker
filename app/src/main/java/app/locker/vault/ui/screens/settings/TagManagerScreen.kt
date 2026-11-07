package app.locker.vault.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.theme.BrandAmber
import kotlinx.coroutines.launch

private data class TagSummary(val name: String, val count: Int)

@Composable
fun TagManagerScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by app.repository.items.collectAsStateWithLifecycle()

    val tags = remember(items) {
        items
            .filter { it.deletedAt == null }
            .flatMap { item -> item.tags }
            .groupBy { it.lowercase() }
            .map { (_, names) -> TagSummary(name = names.first(), count = names.size) }
            .sortedBy { it.name.lowercase() }
    }

    var editing by remember { mutableStateOf<TagSummary?>(null) }
    var deleting by remember { mutableStateOf<TagSummary?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    SettingsScaffold(title = "Tags", onBack = onBack) {
        Spacer(Modifier.height(6.dp))
        InfoBanner(
            icon = Icons.Rounded.Sell,
            tint = BrandAmber,
            title = "One vocabulary across the vault",
            text = "Rename a tag to fix a typo, or rename it to an existing tag to merge them. Deleting a tag removes it from items but never deletes the items themselves."
        )
        Spacer(Modifier.height(16.dp))

        if (tags.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Sell,
                title = "No tags yet",
                subtitle = "Add tags while editing a secret, note, thing, or file. They will appear here for renaming and cleanup."
            )
        } else {
            SettingsGroup {
                tags.forEachIndexed { index, tag ->
                    SettingRow(
                        icon = Icons.Rounded.Sell,
                        tint = BrandAmber,
                        title = "#${tag.name}",
                        subtitle = if (tag.count == 1) "1 item" else "${tag.count} items",
                        onClick = {
                            renameValue = tag.name
                            editing = tag
                        }
                    )
                    if (index < tags.lastIndex) RowDivider()
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${tags.size} tag${if (tags.size == 1) "" else "s"} · tap one to rename or merge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        Spacer(Modifier.height(34.dp))
    }

    val currentlyEditing = editing
    if (currentlyEditing != null) {
        AlertDialog(
            onDismissRequest = { if (!working) editing = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.Sell, contentDescription = null) },
            title = {
                Text(
                    "Rename #${currentlyEditing.name}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Column {
                    Text(
                        "Used on ${currentlyEditing.count} item${if (currentlyEditing.count == 1) "" else "s"}. If another tag already has this name, both are merged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    LockerTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        placeholder = "Tag name",
                        label = "New name"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank() && !working,
                    onClick = {
                        val from = currentlyEditing.name
                        val to = renameValue
                        working = true
                        scope.launch {
                            val result = app.repository.renameTag(from, to)
                            working = false
                            editing = null
                            result.onSuccess { changed ->
                                android.widget.Toast.makeText(
                                    context,
                                    if (changed == 0) {
                                        "Tag unchanged"
                                    } else {
                                        "Renamed #$from on $changed item${if (changed == 1) "" else "s"}"
                                    },
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "Could not rename tag",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(if (working) "Saving…" else "Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        enabled = !working,
                        onClick = {
                            editing = null
                            deleting = currentlyEditing
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editing = null }, enabled = !working) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    val currentlyDeleting = deleting
    if (currentlyDeleting != null) {
        AlertDialog(
            onDismissRequest = { if (!working) deleting = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete #${currentlyDeleting.name}?") },
            text = {
                Text(
                    "The tag is removed from ${currentlyDeleting.count} item${if (currentlyDeleting.count == 1) "" else "s"}. Nothing else in the vault is deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        val tag = currentlyDeleting.name
                        working = true
                        scope.launch {
                            val result = app.repository.deleteTag(tag)
                            working = false
                            deleting = null
                            result.onSuccess { changed ->
                                android.widget.Toast.makeText(
                                    context,
                                    "Removed #$tag from $changed item${if (changed == 1) "" else "s"}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "Could not delete tag",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Delete tag", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, enabled = !working) { Text("Cancel") }
            }
        )
    }
}
