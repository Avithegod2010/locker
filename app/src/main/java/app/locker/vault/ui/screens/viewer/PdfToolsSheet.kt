package app.locker.vault.ui.screens.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.CollectionLocks
import app.locker.vault.data.NormalizedRect
import app.locker.vault.data.PdfGeometry
import app.locker.vault.data.PdfRanges
import app.locker.vault.data.PdfTools
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class PdfToolMode { MENU, EXTRACT, MERGE, REDACT }

/**
 * PDF surgery for the document currently open in the viewer. Every result is written as a new
 * encrypted item; the original is never modified, so an accidental redaction or split costs
 * nothing. All work happens on-device through [PdfTools].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsSheet(
    item: VaultItem,
    sourceFile: File,
    totalPages: Int,
    onDismiss: () -> Unit,
    onCreated: (VaultItem) -> Unit
) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collections by app.repository.collections.collectAsStateWithLifecycle()
    val unlocked by app.unlockedCollections.collectAsStateWithLifecycle()
    val allItems by app.repository.items.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(PdfToolMode.MENU) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var rangeText by remember { mutableStateOf(if (totalPages > 1) "1-${totalPages}" else "1") }
    var mergeTargets by remember { mutableStateOf<Set<String>>(emptySet()) }
    var redactPage by remember { mutableStateOf(1) }
    var redactBoxes by remember { mutableStateOf<List<NormalizedRect>>(emptyList()) }

    val otherPdfs = remember(allItems, collections, unlocked) {
        allItems.filter {
            it.id != item.id &&
                it.isPdf &&
                it.deletedAt == null &&
                it.archivedAt == null &&
                !CollectionLocks.isItemHidden(it, collections, unlocked)
        }
    }

    fun save(label: String, title: String, generate: suspend (File) -> Boolean) {
        if (busyLabel != null) return
        busyLabel = label
        error = null
        scope.launch {
            val out = File(context.cacheDir, "pdf-tool-${System.currentTimeMillis()}.pdf")
            val ok = runCatching { generate(out) }.getOrDefault(false)
            if (!ok || !out.exists() || out.length() == 0L) {
                out.delete()
                error = "Could not build the PDF. The document may be corrupt or too large."
                busyLabel = null
                return@launch
            }
            val imported = app.repository.importFile(
                uri = Uri.fromFile(out),
                type = VaultType.FILE,
                title = title,
                collectionIds = item.collectionIds,
                favorite = false
            )
            out.delete()
            busyLabel = null
            imported
                .onSuccess(onCreated)
                .onFailure { error = it.message ?: "Could not save the new document" }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (busyLabel == null) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "PDF tools",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Results are saved as new encrypted documents. The original is untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            if (busyLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        busyLabel ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            when (mode) {
                PdfToolMode.MENU -> {
                    ToolRow(
                        title = "Extract pages",
                        subtitle = "Save part of this document as its own PDF.",
                        action = "Choose"
                    ) { mode = PdfToolMode.EXTRACT }
                    ToolRow(
                        title = "Merge",
                        subtitle = "Combine this document with another PDF in the vault.",
                        action = if (otherPdfs.isEmpty()) "None" else "Choose"
                    ) { if (otherPdfs.isNotEmpty()) mode = PdfToolMode.MERGE }
                    ToolRow(
                        title = "Compress",
                        subtitle = "Re-render the pages smaller. Text becomes an image.",
                        action = "Compress"
                    ) {
                        save("Compressing…", "${item.title} (small)") { out ->
                            PdfTools.compress(sourceFile, out)
                        }
                    }
                    ToolRow(
                        title = "Redact a page",
                        subtitle = "Draw black boxes, then rebuild the document without that text.",
                        action = "Redact"
                    ) { mode = PdfToolMode.REDACT }
                }

                PdfToolMode.EXTRACT -> {
                    LockerTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it },
                        placeholder = "Pages",
                        label = "Pages to keep",
                        supportingText = PdfRanges.hint(totalPages)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { mode = PdfToolMode.MENU }) { Text("Back") }
                        Spacer(Modifier.weight(1f))
                        val selected = remember(rangeText, totalPages) {
                            PdfRanges.parse(rangeText, totalPages)
                        }
                        TextButton(
                            enabled = selected.isNotEmpty() && busyLabel == null,
                            onClick = {
                                save("Extracting…", "${item.title} (pages)") { out ->
                                    PdfTools.extractPages(sourceFile, selected, out)
                                }
                            }
                        ) { Text("Save selection") }
                    }
                }

                PdfToolMode.MERGE -> {
                    Text(
                        "Merge with",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    otherPdfs.forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = candidate.id in mergeTargets,
                                onCheckedChange = { checked ->
                                    mergeTargets = if (checked) mergeTargets + candidate.id
                                    else mergeTargets - candidate.id
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    candidate.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { mode = PdfToolMode.MENU }) { Text("Back") }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = mergeTargets.isNotEmpty() && busyLabel == null,
                            onClick = {
                                val extraFiles = mergeTargets.mapNotNull { id ->
                                    allItems.firstOrNull { it.id == id }
                                }
                                save("Merging…", "${item.title} (merged)") { out ->
                                    val files = mutableListOf(item)
                                    files += extraFiles
                                    val decrypted = mutableListOf<File>()
                                    try {
                                        files.forEach { candidate ->
                                            val file = if (candidate.id == item.id) sourceFile
                                            else app.repository.decryptToShareCache(candidate)
                                            if (file != null) decrypted += file
                                        }
                                        if (decrypted.size < 2) return@save false
                                        PdfTools.merge(decrypted, out)
                                    } finally {
                                        decrypted.filter { it != sourceFile }.forEach { it.delete() }
                                    }
                                }
                            }
                        ) { Text("Merge") }
                    }
                }

                PdfToolMode.REDACT -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Page",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { redactPage = (redactPage - 1).coerceAtLeast(1) },
                            enabled = redactPage > 1
                        ) { Icon(Icons.Rounded.Remove, contentDescription = "Previous page") }
                        Text(
                            "$redactPage / $totalPages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { redactPage = (redactPage + 1).coerceAtMost(totalPages) },
                            enabled = redactPage < totalPages
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Next page") }
                        Spacer(Modifier.weight(1f))
                        if (redactBoxes.isNotEmpty()) {
                            TextButton(onClick = { redactBoxes = redactBoxes.dropLast(1) }) {
                                Text("Undo")
                            }
                        }
                    }
                    RedactionCanvas(
                        file = sourceFile,
                        pageNumber = redactPage,
                        boxes = redactBoxes,
                        onAdd = { redactBoxes = redactBoxes + it }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Boxes are burned into the page image, and every page is rebuilt, so the " +
                            "covered text is removed rather than hidden. This cannot be undone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { mode = PdfToolMode.MENU }) { Text("Back") }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = redactBoxes.isNotEmpty() && busyLabel == null,
                            onClick = {
                                val boxes = redactBoxes
                                save("Redacting…", "${item.title} (redacted)") { out ->
                                    PdfTools.redactPage(sourceFile, redactPage, boxes, out)
                                }
                            }
                        ) { Text("Apply redaction") }
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToolRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

/**
 * A rendered page you can drag across to place redaction boxes. The bitmap is only used for
 * display; the boxes are stored in page-relative coordinates and burned in by [PdfTools].
 */
@Composable
private fun RedactionCanvas(
    file: File,
    pageNumber: Int,
    boxes: List<NormalizedRect>,
    onAdd: (NormalizedRect) -> Unit
) {
    var bitmap by remember(file, pageNumber) { mutableStateOf<Bitmap?>(null) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    androidx.compose.runtime.LaunchedEffect(file, pageNumber) {
        bitmap = withContext(Dispatchers.IO) {
            PdfTools.renderPage(file, pageNumber - 1, widthPx = 1200)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(pageNumber, viewSize) {
                if (viewSize.width <= 0f) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragStart = it; dragEnd = it },
                    onDrag = { change, _ -> dragEnd = change.position },
                    onDragEnd = {
                        val start = dragStart
                        val end = dragEnd
                        if (start != null && end != null) {
                            val rect = PdfGeometry.normalize(
                                start.x, start.y, end.x, end.y,
                                viewSize.width, viewSize.height
                            )
                            if (PdfGeometry.isMeaningful(rect)) onAdd(rect)
                        }
                        dragStart = null
                        dragEnd = null
                    },
                    onDragCancel = { dragStart = null; dragEnd = null }
                )
            }
    ) {
        val current = bitmap
        if (current == null) {
            Box(Modifier.height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page $pageNumber",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Canvas(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
            boxes.forEach { box ->
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(box.left * size.width, box.top * size.height),
                    size = Size(box.width * size.width, box.height * size.height)
                )
            }
            val start = dragStart
            val end = dragEnd
            if (start != null && end != null) {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                drawRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(left, top),
                    size = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
                )
            }
        }
    }
}
