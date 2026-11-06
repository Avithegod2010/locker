package app.locker.vault.ui.screens.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.data.LegacyContact
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.theme.BrandCoral
import kotlinx.coroutines.launch

@Composable
fun LegacyScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val contacts by app.repository.legacyContacts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<LegacyContact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LegacyContact?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(
            title = "Legacy",
            onBack = onBack,
            actions = {
                IconButton(onClick = { adding = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add legacy contact",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = BrandCoral.copy(alpha = 0.09f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = BrandCoral,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "For the people you trust",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Keep emergency contacts and instructions here. Everything is encrypted on this device and nothing is shared automatically. Give someone you trust your recovery key separately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Person,
                        title = "No legacy contacts",
                        subtitle = "Add a trusted person and the details they may need in an emergency."
                    )
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    LegacyCard(
                        contact = contact,
                        onEdit = { editing = contact },
                        onDelete = { confirmDelete = contact }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (adding || editing != null) {
        LegacyEditorDialog(
            existing = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSaved = {
                adding = false
                editing = null
            }
        )
    }

    confirmDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Remove legacy contact?") },
            text = {
                Text(
                    "Remove ${contact.name} from your legacy contacts?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = contact
                    confirmDelete = null
                    scope.launch { app.repository.deleteLegacyContact(current.id) }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LegacyCard(
    contact: LegacyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LockerCard(
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(BrandCoral.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleLarge,
                    color = BrandCoral
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.relationship.isNotBlank()) {
                    Text(
                        contact.relationship,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit contact",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove contact",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (contact.contact.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    contact.contact,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                )
            }
        }
        if (contact.notes.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(
                contact.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun LegacyEditorDialog(
    existing: LegacyContact?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var relationship by remember { mutableStateOf(existing?.relationship.orEmpty()) }
    var contact by remember { mutableStateOf(existing?.contact.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                if (existing == null) "Add legacy contact" else "Edit legacy contact",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                LockerTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    placeholder = "Full name",
                    label = "Name",
                    required = true
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    placeholder = "Partner, sibling, lawyer…",
                    label = "Relationship"
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    placeholder = "Phone or email",
                    label = "Contact"
                )
                Spacer(Modifier.height(14.dp))
                LockerTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "What should they know?",
                    label = "Notes",
                    minLines = 3,
                    maxLines = 6
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
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val result = if (existing == null) {
                            app.repository.addLegacyContact(name, relationship, contact, notes)
                        } else {
                            app.repository.updateLegacyContact(
                                existing.copy(
                                    name = name.trim(),
                                    relationship = relationship.trim(),
                                    contact = contact.trim(),
                                    notes = notes.trim()
                                )
                            )
                        }
                        saving = false
                        result.onSuccess { onSaved() }
                            .onFailure { error = it.message ?: "Could not save contact" }
                    }
                }
            ) {
                Text(if (existing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
