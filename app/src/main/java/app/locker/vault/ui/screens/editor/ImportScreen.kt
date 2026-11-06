package app.locker.vault.ui.screens.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.Formatting
import app.locker.vault.data.PickedFileInfo
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.accent
import app.locker.vault.ui.theme.BrandMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PickedMedia(
    val uri: Uri,
    val info: PickedFileInfo
) {
    val isImage: Boolean get() = info.mimeType.startsWith("image/")
    val isVideo: Boolean get() = info.mimeType.startsWith("video/")
}

@Composable
fun ImportScreen(
    type: VaultType,
    onBack: () -> Unit
) {
    require(type == VaultType.PHOTO || type == VaultType.FILE)
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collections by app.repository.collections.collectAsStateWithLifecycle()

    var picks by remember { mutableStateOf<List<PickedMedia>>(emptyList()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var title by remember { mutableStateOf("") }
    var selectedCollections by remember { mutableStateOf(emptySet<String>()) }
    var favorite by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoOpened by rememberSaveable { mutableStateOf(false) }

    fun applyPicks(uris: List<Uri>) {
        val values = uris.distinct().map { PickedMedia(it, app.fileStore.describe(it)) }
        if (values.isEmpty()) return
        picks = values
        title = if (values.size == 1) {
            values.first().info.displayName.substringBeforeLast('.').ifBlank {
                if (type == VaultType.PHOTO) Formatting.photoTitle() else "Untitled file"
            }
        } else {
            ""
        }
        error = null
        preview = null
        val first = values.first()
        if (first.isImage) {
            scope.launch { preview = loadPreview(context, first.uri) }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { picked ->
        app.session.endExternalActivity()
        applyPicks(picked)
    }

    fun pick() {
        app.session.beginExternalActivity()
        runCatching {
            fileLauncher.launch(
                if (type == VaultType.PHOTO) {
                    arrayOf("image/*", "video/*")
                } else {
                    arrayOf("*/*")
                }
            )
        }.onFailure {
            app.session.endExternalActivity()
            error = "Could not open the system picker: ${it.message ?: "unknown error"}"
        }
    }

    LaunchedEffect(Unit) {
        val shared = app.pendingImport.value
        if (shared.isNotEmpty()) {
            app.pendingImport.value = emptyList()
            autoOpened = true
            applyPicks(shared)
        } else if (!autoOpened) {
            autoOpened = true
            delay(250)
            pick()
        }
    }

    EditorScaffold(
        title = if (type == VaultType.PHOTO) "Photo or video" else "Files",
        onBack = onBack,
        saveEnabled = picks.isNotEmpty(),
        saving = saving,
        saveLabel = if (saving) "Encrypting…" else "Save",
        onSave = {
            if (picks.isNotEmpty() && !saving) {
                saving = true
                error = null
                scope.launch {
                    var saved = 0
                    var failure: String? = null
                    picks.forEachIndexed { index, media ->
                        val itemTitle = when {
                            picks.size == 1 -> title
                            title.isBlank() -> media.info.displayName.substringBeforeLast('.')
                            else -> "$title ${index + 1}"
                        }
                        val result = app.repository.importFile(
                            uri = media.uri,
                            type = type,
                            title = itemTitle,
                            collectionIds = selectedCollections.toList(),
                            favorite = favorite
                        )
                        result.onSuccess { saved++ }
                            .onFailure { failure = it.message ?: "Could not import ${media.info.displayName}" }
                    }
                    saving = false
                    if (saved == picks.size) {
                        onBack()
                    } else {
                        error = if (saved == 0) {
                            failure ?: "Could not import the selected item"
                        } else {
                            "Imported $saved of ${picks.size}. $failure"
                        }
                    }
                }
            }
        }
    ) {
        Spacer(Modifier.height(14.dp))
        if (picks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (type == VaultType.PHOTO) Icons.Rounded.Image else Icons.Rounded.UploadFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (type == VaultType.PHOTO) "Choose photos or videos" else "Choose files",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (type == VaultType.PHOTO) {
                            "Pick up to 20 photos or videos. They are encrypted on this device before saving."
                        } else {
                            "Pick one or more files. They are encrypted on this device before saving."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        onClick = { pick() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            "Browse",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        } else {
            val first = picks.first()
            if (preview != null && first.isImage) {
                Image(
                    bitmap = preview!!.asImageBitmap(),
                    contentDescription = "Selected media preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(26.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(
                            icon = when {
                                first.isVideo -> Icons.Rounded.PlayCircle
                                type == VaultType.PHOTO -> Icons.Rounded.Image
                                else -> Icons.Rounded.Description
                            },
                            tint = type.accent(),
                            size = 52.dp
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (picks.size == 1) first.info.displayName else "${picks.size} items selected",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    if (picks.size == 1) {
                                        if (first.info.sizeBytes > 0) {
                                            append(Formatting.bytes(first.info.sizeBytes))
                                            append(" · ")
                                        }
                                        append(first.info.mimeType)
                                    } else {
                                        append("Tap Save to encrypt them all")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (picks.size == 1) first.info.displayName else "${picks.size} items ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { pick() }) {
                    Text("Change", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (picks.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(picks, key = { it.uri.toString() }) { media ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (media.isVideo) {
                                    Icon(
                                        Icons.Rounded.PlayCircle,
                                        contentDescription = null,
                                        tint = BrandMint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    media.info.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        LockerTextField(
            value = title,
            onValueChange = { title = it; error = null },
            placeholder = if (picks.size > 1) "Optional title prefix" else "Give this a title",
            label = if (picks.size > 1) "Title prefix" else "Title",
            required = picks.size == 1
        )

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

        Spacer(Modifier.height(20.dp))
        ImportantToggle(checked = favorite, onCheckedChange = { favorite = it })

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
            Modifier.fillMaxWidth(),
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
                "Originals stay outside the vault; encrypted copies are stored inside",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

private suspend fun loadPreview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1400 && bounds.outHeight / (sample * 2) >= 1400) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}
