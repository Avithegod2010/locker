package app.locker.vault.ui.screens.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.ItemIcon
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.theme.BrandBlue
import kotlinx.coroutines.launch

@Composable
fun ArchiveScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val archived = items.filter { it.archivedAt != null && it.deletedAt == null }
        .sortedByDescending { it.archivedAt }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackTopBar(title = "Archive", onBack = onBack)
        if (archived.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Archive,
                title = "Archive is empty",
                subtitle = "Archive items you want to keep but not see on the home screen."
            )
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BrandBlue.copy(alpha = 0.09f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "Archived items stay encrypted and searchable from Archive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(archived, key = { it.id }) { item ->
                    LockerCard(
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ItemIcon(item = item, size = 48.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { app.repository.unarchive(item) }
                            }) {
                                Icon(Icons.Rounded.Archive, contentDescription = "Unarchive")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    app.repository.unarchive(item)
                                    app.repository.softDelete(item)
                                }
                            }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Move to trash",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
