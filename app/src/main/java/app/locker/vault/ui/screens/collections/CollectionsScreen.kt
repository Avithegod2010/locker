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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.SavedSearch
import app.locker.vault.core.SavedSearches
import app.locker.vault.data.VaultCollection
import app.locker.vault.data.VaultItem
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.ItemIcon
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.collectionIcon
import app.locker.vault.ui.screens.editor.NewCollectionDialog
import app.locker.vault.ui.theme.BrandBlue
import kotlinx.coroutines.launch

@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val app = LocalAppContainer.current
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val savedSearches = remember(settings.savedSearchesJson) {
        SavedSearches.parse(settings.savedSearchesJson)
    }
    var showCreate by remember { mutableStateOf(false) }
    var showSmartFolder by remember { mutableStateOf(false) }
    var editingSmart by remember { mutableStateOf<SavedSearch?>(null) }
    var smartName by remember { mutableStateOf("") }
    var smartQuery by remember { mutableStateOf("") }
    var smartIcon by remember { mutableStateOf("search") }
    var smartPinned by remember { mutableStateOf(true) }
    val active = items.filter { it.deletedAt == null }
    val roots = collections.filter { it.parentId.isBlank() }

    fun openSmartFolder(existing: SavedSearch?) {
        editingSmart = existing
        smartName = existing?.name.orEmpty()
        smartQuery = existing?.query.orEmpty()
        smartIcon = existing?.icon ?: "search"
        smartPinned = existing?.pinned ?: true
        showSmartFolder = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(
            title = "Collections",
            onBack = onBack,
            actions = {
                IconButton(onClick = { openSmartFolder(null) }) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "New smart folder",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "New collection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        if (roots.isEmpty() && savedSearches.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = "No collections yet",
                subtitle = "Group passwords, notes, and documents into collections. Create one with the + button, or save a search as a smart folder."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (savedSearches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SectionLabel("Smart folders")
                            savedSearches.forEach { search ->
                                SmartFolderRow(
                                    search = search,
                                    onOpen = {
                                        app.pendingSearch.value = search.query
                                        onBack()
                                    },
                                    onEdit = { openSmartFolder(search) },
                                    onDelete = {
                                        app.preferences.setSavedSearchesJson(
                                            SavedSearches.remove(settings.savedSearchesJson, search.id)
                                        )
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            SectionLabel("Collections")
                        }
                    }
                }
                items(roots, key = { it.id }) { collection ->
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

    if (showSmartFolder) {
        AlertDialog(
            onDismissRequest = { showSmartFolder = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (editingSmart == null) "New smart folder" else "Edit smart folder") },
            text = {
                Column {
                    Text(
                        "A smart folder runs a saved search every time you open it — for example type:secret tag:bank.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    LockerTextField(
                        value = smartName,
                        onValueChange = { smartName = it },
                        placeholder = "Banks",
                        label = "Name"
                    )
                    Spacer(Modifier.height(12.dp))
                    LockerTextField(
                        value = smartQuery,
                        onValueChange = { smartQuery = it },
                        placeholder = "type:secret tag:bank",
                        label = "Search query"
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "ICON",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("search", "star", "lock", "folder").forEach { icon ->
                            val selected = smartIcon == icon
                            Surface(
                                onClick = { smartIcon = icon },
                                shape = CircleShape,
                                color = if (selected) {
                                    Color(smartColorFor(icon)).copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
                            ) {
                                Icon(
                                    smartIconFor(icon),
                                    contentDescription = icon,
                                    tint = Color(smartColorFor(icon)),
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pin to Home",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = smartPinned,
                            onCheckedChange = { smartPinned = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = smartQuery.isNotBlank(),
                    onClick = {
                        val json = settings.savedSearchesJson
                        val existing = editingSmart
                        if (existing != null) {
                            app.preferences.setSavedSearchesJson(
                                SavedSearches.update(
                                    json,
                                    existing.copy(
                                        name = smartName.trim().ifBlank { smartQuery.trim() },
                                        query = smartQuery.trim(),
                                        icon = smartIcon,
                                        color = smartColorFor(smartIcon),
                                        pinned = smartPinned
                                    )
                                )
                            )
                        } else {
                            val added = SavedSearches.add(json, smartName, smartQuery)
                            val created = SavedSearches.parse(added).firstOrNull {
                                it.query.equals(smartQuery.trim(), ignoreCase = true)
                            }
                            app.preferences.setSavedSearchesJson(
                                if (created != null) {
                                    SavedSearches.update(
                                        added,
                                        created.copy(
                                            icon = smartIcon,
                                            color = smartColorFor(smartIcon),
                                            pinned = smartPinned
                                        )
                                    )
                                } else {
                                    added
                                }
                            )
                        }
                        smartName = ""
                        smartQuery = ""
                        editingSmart = null
                        showSmartFolder = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSmartFolder = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp)
    )
}

@Composable
private fun SmartFolderRow(
    search: SavedSearch,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LockerCard(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = smartIconFor(search.icon),
                tint = Color(search.color),
                size = 40.dp,
                iconSize = 20.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    search.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    search.query,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit smart folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Delete smart folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun smartIconFor(icon: String) = when (icon) {
    "star" -> Icons.Rounded.Star
    "lock" -> Icons.Rounded.Lock
    "folder" -> Icons.Rounded.Folder
    else -> Icons.Rounded.Search
}

private fun smartColorFor(icon: String): Long = when (icon) {
    "star" -> 0xFFFFB968
    "lock" -> 0xFFFF8F7A
    "folder" -> 0xFF57D9A3
    else -> 0xFF5B9CFF
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
    onOpenItem: (String) -> Unit,
    onOpenCollection: (String) -> Unit = {}
) {
    val app = LocalAppContainer.current
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val collection = remember(collectionId, collections) {
        collections.firstOrNull { it.id == collectionId }
    }
    var showEdit by remember { mutableStateOf(false) }
    var showCreateChild by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (collection == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            BackTopBar(title = "Collection", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This collection no longer exists.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val children = collections.filter { it.parentId == collectionId }
    val matching = items
        .filter { it.deletedAt == null && collectionId in it.collectionIds }
        .sortedByDescending { it.updatedAt }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(
            title = collection.name,
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreateChild = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "New subcollection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showMove = true }) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = "Move collection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

        if (children.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(children, key = { it.id }) { child ->
                    Surface(
                        onClick = { onOpenCollection(child.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = Color(child.color),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                child.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (matching.isEmpty()) {
            if (children.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = "Nothing in here yet",
                    subtitle = "Open an item and add it to this collection from the editor or its ••• menu."
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "Subcollections are shown above. This folder has no items of its own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(28.dp)
                    )
                }
            }
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

    if (showCreateChild) {
        NewCollectionDialog(
            parentId = collection.id,
            onDismiss = { showCreateChild = false },
            onCreated = { showCreateChild = false }
        )
    }

    if (showMove) {
        MoveCollectionDialog(
            collection = collection,
            collections = collections,
            onDismiss = { showMove = false },
            onMoved = { showMove = false }
        )
    }

    if (confirmDelete) {
        DeleteCollectionDialog(
            collection = collection,
            onDismiss = { confirmDelete = false },
            onDeleted = {
                confirmDelete = false
                onBack()
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
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MoveCollectionDialog(
    collection: VaultCollection,
    collections: List<VaultCollection>,
    onDismiss: () -> Unit,
    onMoved: () -> Unit
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val blocked = remember(collection.id, collections) {
        descendantIds(collections, collection.id) + collection.id
    }
    val candidates = collections.filter { it.id !in blocked }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Move \"${collection.name}\"") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Choose a parent collection, or keep it at the top level.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                MoveTargetRow("Top level") {
                    scope.launch {
                        app.repository.setCollectionParent(collection.id, "")
                        onMoved()
                    }
                }
                candidates.forEach { parent ->
                    MoveTargetRow(parent.name) {
                        scope.launch {
                            app.repository.setCollectionParent(collection.id, parent.id)
                            onMoved()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun MoveTargetRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DeleteCollectionDialog(
    collection: VaultCollection,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
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
                scope.launch {
                    app.repository.deleteCollection(collection.id)
                    onDeleted()
                }
            }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun descendantIds(collections: List<VaultCollection>, id: String): Set<String> {
    val result = mutableSetOf<String>()
    fun walk(parent: String) {
        collections.filter { it.parentId == parent }.forEach { child ->
            if (result.add(child.id)) walk(child.id)
        }
    }
    walk(id)
    return result
}
