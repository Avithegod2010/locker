package app.locker.vault.ui.screens.detail

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.Formatting
import app.locker.vault.core.Totp
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.ItemIcon
import app.locker.vault.ui.components.ItemThumbnail
import app.locker.vault.ui.components.accent
import app.locker.vault.ui.components.decodeSampledBitmap
import app.locker.vault.ui.components.icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onEdit: (VaultItem) -> Unit,
    onViewPhoto: (VaultItem) -> Unit,
    onViewVideo: (VaultItem) -> Unit,
    onViewPdf: (VaultItem) -> Unit,
    onEditImage: (VaultItem) -> Unit,
    onOpenAttachment: (VaultItem) -> Unit
) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allItems by app.repository.items.collectAsStateWithLifecycle()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val item = remember(itemId, allItems) { allItems.firstOrNull { it.id == itemId } }
    val itemCollections = collections.filter { item?.collectionIds?.contains(it.id) == true }
    val attachments = remember(itemId, allItems) {
        allItems.filter { it.parentId == itemId && it.deletedAt == null }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var revealPassword by remember { mutableStateOf(false) }
    var revealFields by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var photoPreview by remember { mutableStateOf<Bitmap?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(item?.mimeType?.ifBlank { "application/octet-stream" } ?: "application/octet-stream")
    ) { uri ->
        app.session.endExternalActivity()
        val current = item
        if (current == null) {
            android.widget.Toast.makeText(context, "Item is no longer available", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    app.repository.exportFile(current, output)
                } ?: false
            }.getOrDefault(false)
            busy = false
            android.widget.Toast.makeText(
                context,
                if (ok) "Decrypted copy exported" else "Could not export file",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        app.session.endExternalActivity()
        val parent = item ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            var added = 0
            uris.forEach { uri ->
                val info = app.fileStore.describe(uri)
                val type = if (info.mimeType.startsWith("image/") ||
                    info.mimeType.startsWith("video/")
                ) VaultType.PHOTO else VaultType.FILE
                app.repository.importFile(
                    uri = uri,
                    type = type,
                    title = info.displayName.substringBeforeLast('.'),
                    collectionIds = parent.collectionIds,
                    parentId = parent.id
                ).onSuccess { added++ }
            }
            busy = false
            android.widget.Toast.makeText(
                context,
                if (added > 0) "Encrypted $added attachment${if (added == 1) "" else "s"}" else "Could not attach file",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (item == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            BackTopBar(title = "Item", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This item isn't available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LaunchedEffect(item.id, item.isImage) {
        if (item.isImage) {
            photoPreview = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.repository.readImage(item)
                    bytes?.let { decodeSampledBitmap(it, 1400, 1400) }
                }.getOrNull()
            }
        }
    }

    fun openFile() {
        app.session.beginExternalActivity()
        busy = true
        scope.launch {
            val file = runCatching { app.repository.decryptToShareCache(item) }.getOrNull()
            busy = false
            if (file == null) {
                app.session.endExternalActivity()
                android.widget.Toast.makeText(context, "Could not open file", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            shareIntent(context, item, file, view = true)
            scope.launch {
                delay(60_000)
                app.session.endExternalActivity()
            }
        }
    }

    fun shareFile() {
        app.session.beginExternalActivity()
        busy = true
        scope.launch {
            val file = runCatching { app.repository.decryptToShareCache(item) }.getOrNull()
            busy = false
            if (file == null) {
                app.session.endExternalActivity()
                android.widget.Toast.makeText(context, "Could not prepare file", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            shareIntent(context, item, file, view = false)
            scope.launch {
                delay(60_000)
                app.session.endExternalActivity()
            }
        }
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
            BackTopBar(
                title = item.type.label,
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch { app.repository.toggleFavorite(item) }
                    }) {
                        Icon(
                            if (item.favorite) Icons.Rounded.Star else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Toggle important",
                            tint = if (item.favorite) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            if (item.isImage) {
                                DropdownMenuItem(
                                    text = { Text("Edit image") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onEditImage(item)
                                    }
                                )
                            }
                            if (item.isFileBacked) {
                                DropdownMenuItem(
                                    text = { Text("Share decrypted copy") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmShare = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export decrypted copy") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        app.session.beginExternalActivity()
                                        exportLauncher.launch(
                                            item.fileName.ifBlank {
                                                "${item.title}.${if (item.mimeType.contains("pdf")) "pdf" else "bin"}"
                                            }
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (item.archivedAt == null) "Archive" else "Unarchive") },
                                leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        if (item.archivedAt == null) {
                                            app.repository.archive(item)
                                        } else {
                                            app.repository.unarchive(item)
                                        }
                                        onBack()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                }
                            )
                        }
                    }
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                when (item.type) {
                    VaultType.SECRET -> SecretDetail(
                        item = item,
                        revealPassword = revealPassword,
                        onToggleReveal = { revealPassword = !revealPassword },
                        onCopy = { label, value ->
                            app.secureClipboard.copy(label, value, app.preferences.settings.value.clipboardClearSeconds)
                            android.widget.Toast.makeText(context, "$label copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    VaultType.NOTE -> NoteDetail(item = item, onCopy = {
                        app.secureClipboard.copy(
                            "Locker note",
                            item.body,
                            app.preferences.settings.value.clipboardClearSeconds
                        )
                        android.widget.Toast.makeText(context, "Note copied", android.widget.Toast.LENGTH_SHORT).show()
                    })

                    VaultType.THING -> ThingDetail(item = item, onCopy = { label, value ->
                        app.secureClipboard.copy(label, value, app.preferences.settings.value.clipboardClearSeconds)
                        android.widget.Toast.makeText(context, "$label copied", android.widget.Toast.LENGTH_SHORT).show()
                    })

                    VaultType.FILE, VaultType.PHOTO -> FileDetail(
                        item = item,
                        preview = photoPreview,
                        onOpen = {
                            when {
                                item.isVideo -> onViewVideo(item)
                                item.isPdf -> onViewPdf(item)
                                item.isImage -> onViewPhoto(item)
                                else -> openFile()
                            }
                        },
                        onShare = { confirmShare = true },
                        onExport = {
                            app.session.beginExternalActivity()
                            exportLauncher.launch(
                                item.fileName.ifBlank { "${item.title}.bin" }
                            )
                        }
                    )
                }

                if (itemCollections.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "COLLECTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemCollections.forEach { collection ->
                            Surface(
                                shape = CircleShape,
                                color = Color(collection.color).copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = Color(collection.color),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        collection.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "TAGS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (item.fields.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "CUSTOM FIELDS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.fields.any { it.sensitive }) {
                            TextButton(onClick = { revealFields = !revealFields }) {
                                Text(
                                    if (revealFields) "Hide" else "Reveal",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    item.fields.forEach { field ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    field.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (field.sensitive && !revealFields) {
                                        "•".repeat(field.value.length.coerceIn(6, 20))
                                    } else {
                                        field.value
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (attachments.isNotEmpty() || item.type == VaultType.SECRET ||
                    item.type == VaultType.NOTE || item.type == VaultType.THING
                ) {
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ATTACHMENTS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            app.session.beginExternalActivity()
                            attachLauncher.launch(arrayOf("*/*"))
                        }) {
                            Text("Add", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (attachments.isEmpty()) {
                        Text(
                            "Attach a photo, receipt, PDF, or any file to this item.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    } else {
                        attachments.forEach { attachment ->
                            Surface(
                                onClick = { onOpenAttachment(attachment) },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ItemIcon(item = attachment, size = 44.dp)
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            attachment.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${Formatting.bytes(attachment.sizeBytes)} · " +
                                                attachment.mimeType.substringAfter('/').uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))
                Text(
                    "Created ${Formatting.fullDate(item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (item.updatedAt != item.createdAt) {
                    Text(
                        "Edited ${Formatting.relativeDate(item.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(38.dp))
            }
        }

        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(20.dp).size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Move to trash?") },
            text = {
                Text(
                    "\"${item.title}\" will stay in Trash for 30 days before it's deleted permanently.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.repository.softDelete(item)
                        onBack()
                    }
                }) {
                    Text("Move to trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            title = { Text("Share a decrypted copy?") },
            text = {
                Text(
                    "Locker will decrypt this file into the app cache so another app can read it. The temporary copy is removed when the vault locks.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    shareFile()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmShare = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TotpCard(secret: String, onCopy: (String, String) -> Unit) {
    val config = remember(secret) { Totp.parse(secret) }
    if (config == null) return
    DisposableEffect(config) {
        onDispose { config.wipe() }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(config) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val code = remember(config, now / 1000L) { Totp.code(config, now) }
    val remaining = Totp.secondsRemaining(config, now)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ONE-TIME PASSWORD",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onCopy("One-time code", code) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy one-time code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { remaining.toFloat() / config.period.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Refreshes in ${remaining}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecretDetail(
    item: VaultItem,
    revealPassword: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            icon = Icons.Rounded.Lock,
            tint = VaultType.SECRET.accent(),
            size = 64.dp,
            iconSize = 31.dp
        )
        Spacer(Modifier.width(15.dp))
        Column {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.username.isNotBlank()) {
                Text(
                    item.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    if (item.username.isNotBlank()) {
        DetailCard(
            label = "USERNAME",
            value = item.username,
            monospace = false,
            onCopy = { onCopy("Username", item.username) }
        )
        Spacer(Modifier.height(12.dp))
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PASSWORD",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleReveal, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealPassword) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = { onCopy("Password", item.password) },
                    modifier = Modifier.size(30.dp),
                    enabled = item.password.isNotBlank()
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy password",
                        tint = if (item.password.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (item.password.isBlank()) "No password saved"
                else if (revealPassword) item.password
                else "•".repeat(item.password.length.coerceIn(8, 22)),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = if (revealPassword) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = if (revealPassword) 0.sp else 2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (item.otpSecret.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        TotpCard(secret = item.otpSecret, onCopy = onCopy)
    }

    if (item.body.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "NOTES",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    label: String,
    value: String,
    monospace: Boolean,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NoteDetail(item: VaultItem, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            icon = Icons.Rounded.StickyNote2,
            tint = VaultType.NOTE.accent(),
            size = 64.dp,
            iconSize = 31.dp
        )
        Spacer(Modifier.width(15.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Encrypted note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy note",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                item.body,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun ThingDetail(item: VaultItem, onCopy: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            icon = Icons.Rounded.Folder,
            tint = VaultType.THING.accent(),
            size = 64.dp,
            iconSize = 31.dp
        )
        Spacer(Modifier.width(15.dp))
        Column {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Saved thing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (item.location.isNotBlank()) {
        Spacer(Modifier.height(24.dp))
        DetailCard(
            label = "LOCATION",
            value = item.location,
            monospace = false,
            onCopy = { onCopy("Location", item.location) }
        )
    }
    if (item.body.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "NOTES",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FileDetail(
    item: VaultItem,
    preview: Bitmap?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    if (item.isVideo) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(78.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayCircle,
                                contentDescription = "Play video",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Play encrypted video",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Decrypted into a short-lived cache only while you watch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    } else if (preview != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                onClick = onOpen,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    } else if (item.isImage) {
        ItemThumbnail(item = item, size = 112.dp)
        Spacer(Modifier.height(16.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (preview == null) {
            IconTile(
                icon = item.type.icon(),
                tint = item.type.accent(),
                size = 64.dp,
                iconSize = 31.dp
            )
            Spacer(Modifier.width(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(Formatting.bytes(item.sizeBytes))
                    if (item.mimeType.isNotBlank()) {
                        append(" · ")
                        append(item.mimeType)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (item.fileName.isNotBlank() && item.fileName != item.title) {
        Spacer(Modifier.height(8.dp))
        Text(
            item.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        ActionChip(
            icon = when {
                item.isVideo -> Icons.Rounded.PlayCircle
                item.isPdf -> Icons.Rounded.Description
                else -> Icons.Rounded.OpenInNew
            },
            label = when {
                item.isVideo -> "Play"
                item.isPdf -> "Read"
                item.isImage -> "View"
                else -> "Open"
            },
            onClick = onOpen,
            modifier = Modifier.weight(1f)
        )
        ActionChip(
            icon = Icons.Rounded.Share,
            label = "Share",
            onClick = onShare,
            modifier = Modifier.weight(1f)
        )
        ActionChip(
            icon = Icons.Rounded.Download,
            label = "Export",
            onClick = onExport,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun shareIntent(
    context: android.content.Context,
    item: VaultItem,
    file: File,
    view: Boolean
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = if (view) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(
            if (view) intent else Intent.createChooser(intent, "Share decrypted copy")
        )
    }.onFailure {
        android.widget.Toast.makeText(context, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show()
    }
}
