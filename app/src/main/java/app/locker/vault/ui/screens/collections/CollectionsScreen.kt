package app.locker.vault.ui.screens.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.data.VaultCollection
import app.locker.vault.data.VaultItem
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.ItemIcon
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.components.collectionIcon
import app.locker.vault.ui.screens.editor.NewCollectionDialog
import kotlinx.coroutines.launch

@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val app = LocalAppContainer.current
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val active = items.filter { it.deletedAt == null }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(
            title = "Collections",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "New collection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        if (collections.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = "No collections yet",
                subtitle = "Group passwords, notes, and documents into collections. Create one with the + button."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionCard(
                        collection = collection,
                        count = active.count { collection.id in it.collectionIds },
                        onClick = { onOpenCollection(collection.id) }
                    )
                }
            }
        }
    }

    if (showCreate) {
        NewCollectionDialog(
            onDismiss = { showCreate = false },
            onCreated = { showCreate = false }
        )
    }
}

@Composable
private fun CollectionCard(
    collection: VaultCollection,
    count: Int,
    onClick: () -> Unit
) {
    val accent = Color(collection.color)
    LockerCard(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.height(152.dp)
    ) {
        IconTile(
            icon = collectionIcon(collection.icon),
            tint = accent,
            size = 44.dp,
            iconSize = 22.dp
        )
        Spacer(Modifier.weight(1f))
        Text(
            collection.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (count == 1) "1 item" else "$count items",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val collection = remember(collectionId, collections) {
        collections.firstOrNull { it.id == collectionId }
    }
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val matching = items.filter {
        it.deletedAt == null && collectionId in it.collectionIds
    }.sortedByDescending { it.updatedAt }

    if (collection == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            BackTopBar(title = "Collection", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This collection no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(
            title = collection.name,
            onBack = onBack,
            actions = {
                IconButton(onClick = { showEdit = true }) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit collection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete collection",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
        if (matching.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = "Nothing in here yet",
                subtitle = "Open an item and add it to this collection from the editor or its ••• menu."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text(
                        if (matching.size == 1) "1 item" else "${matching.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                items(matching, key = { it.id }) { item ->
                    CollectionItemRow(item = item, onClick = { onOpenItem(item.id) })
                }
            }
        }
    }

    if (showEdit) {
        NewCollectionDialog(
            existing = collection,
            onDismiss = { showEdit = false },
            onCreated = { showEdit = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete collection?") },
            text = {
                Text(
                    "The items inside \"${collection.name}\" will stay in your vault. Only the collection is removed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.repository.deleteCollection(collection.id)
                        onBack()
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CollectionItemRow(item: VaultItem, onClick: () -> Unit) {
    LockerCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(item = item, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
