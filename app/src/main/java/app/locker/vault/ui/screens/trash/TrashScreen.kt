package app.locker.vault.ui.screens.trash

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.Formatting
import app.locker.vault.data.VaultItem
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.ItemIcon
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.theme.BrandAmber
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allItems by app.repository.items.collectAsStateWithLifecycle()
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val trashed = allItems.filter { it.deletedAt != null }
        .sortedByDescending { it.deletedAt }
    var confirmItem by remember { mutableStateOf<VaultItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        app.repository.purgeExpiredTrash()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(
            title = "Trash",
            onBack = onBack,
            actions = {
                if (trashed.isNotEmpty()) {
                    IconButton(onClick = { confirmEmpty = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Empty trash",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BrandAmber.copy(alpha = 0.09f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = BrandAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (settings.autoPurgeTrash) {
                        "Trash is encrypted too. Items are permanently deleted after 30 days."
                    } else {
                        "Trash is encrypted too. Items stay until you delete them forever."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        if (trashed.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.DeleteForever,
                title = "Trash is empty",
                subtitle = "Items you delete will appear here for 30 days before being permanently erased."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(trashed, key = { it.id }) { item ->
                    TrashItemRow(
                        item = item,
                        onRestore = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    val result = app.repository.restore(item)
                                    busy = false
                                    android.widget.Toast.makeText(
                                        context,
                                        if (result.isSuccess) "Restored \"${item.title}\"" else "Could not restore",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onDelete = { confirmItem = item }
                    )
                }
            }
        }
    }

    confirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete forever?") },
            text = {
                Text(
                    "\"${item.title}\" will be permanently erased. This can't be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmItem = null
                    scope.launch {
                        app.repository.deleteForever(item)
                    }
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmItem = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Empty trash?") },
            text = {
                Text(
                    "All ${trashed.size} items in Trash will be permanently erased.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    busy = true
                    scope.launch {
                        app.repository.emptyTrash()
                        busy = false
                    }
                }) { Text("Empty trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashItemRow(
    item: VaultItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    LockerCard(
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
                Text(
                    "Deleted ${item.deletedAt?.let(Formatting::relativeDate) ?: "recently"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onRestore, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = "Delete forever",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
