package app.locker.vault.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.components.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Draws opaque black boxes over a decrypted photo and saves the result as a new encrypted
 * PHOTO item. The original item is never modified, and the temporary bitmap/file stays in
 * the app cache only for the duration of the save.
 */
@Composable
fun RedactPhotoScreen(
    itemId: String,
    onBack: () -> Unit
) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SecureWindow()
    val allItems by app.repository.items.collectAsStateWithLifecycle()
    val item = remember(itemId, allItems) {
        allItems.firstOrNull { it.id == itemId && it.isImage }
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var boxes by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var dragBox by remember { mutableStateOf<RectF?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = item?.let { app.repository.readImage(it) } ?: return@runCatching null
                decodeSampledBitmap(bytes, 2400, 2400)
            }.getOrNull()
        }
        if (loaded == null) error = "This photo could not be opened." else bitmap = loaded
    }

    fun save() {
        val source = bitmap ?: return
        val currentItem = item ?: return
        if (saving || boxes.isEmpty()) return
        saving = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = Bitmap.createBitmap(
                        source.width,
                        source.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(output)
                    canvas.drawBitmap(source, 0f, 0f, null)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        style = Paint.Style.FILL
                    }
                    boxes.forEach { canvas.drawRect(it, paint) }
                    val file = File(context.cacheDir, "redacted-${System.currentTimeMillis()}.png")
                    try {
                        FileOutputStream(file).use { stream ->
                            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        }
                        app.repository.importFile(
                            uri = Uri.fromFile(file),
                            type = VaultType.PHOTO,
                            title = "${currentItem.title} (redacted)",
                            collectionIds = currentItem.collectionIds
                        ).getOrThrow()
                    } finally {
                        file.delete()
                        output.recycle()
                    }
                }
            }
            saving = false
            result.onSuccess { onBack() }
                .onFailure { error = it.message ?: "Could not save the redacted photo" }
        }
    }

    EditorScaffold(
        title = "Redact photo",
        onBack = onBack,
        saveEnabled = bitmap != null && boxes.isNotEmpty(),
        saving = saving,
        saveLabel = if (saving) "Saving…" else "Save copy",
        onSave = { save() }
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            "Drag across the photo to draw black boxes over names, numbers, or anything private. Saving creates a new encrypted photo; the original stays unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        val image = bitmap
        if (image == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                if (error == null) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(image.width.toFloat() / image.height.toFloat())
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(image, canvasSize) {
                            if (canvasSize.width == 0) return@pointerInput
                            val currentScale = canvasSize.width.toFloat() / image.width
                            detectDragGestures(
                                onDragStart = { position ->
                                    val x = (position.x / currentScale)
                                        .coerceIn(0f, image.width.toFloat())
                                    val y = (position.y / currentScale)
                                        .coerceIn(0f, image.height.toFloat())
                                    dragBox = RectF(x, y, x, y)
                                },
                                onDragEnd = {
                                    val box = dragBox
                                    if (box != null &&
                                        box.width() > 6f && box.height() > 6f
                                    ) {
                                        boxes = boxes + RectF(box)
                                    }
                                    dragBox = null
                                },
                                onDragCancel = { dragBox = null },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val box = dragBox ?: return@detectDragGestures
                                    val x = (change.position.x / currentScale)
                                        .coerceIn(0f, image.width.toFloat())
                                    val y = (change.position.y / currentScale)
                                        .coerceIn(0f, image.height.toFloat())
                                    dragBox = RectF(
                                        minOf(box.left, x),
                                        minOf(box.top, y),
                                        maxOf(box.left, x),
                                        maxOf(box.top, y)
                                    )
                                }
                            )
                        }
                ) {
                    drawImage(
                        image = image.asImageBitmap(),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                    val drawScale = size.width / image.width
                    boxes.forEach { box ->
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(box.left * drawScale, box.top * drawScale),
                            size = Size(
                                (box.right - box.left) * drawScale,
                                (box.bottom - box.top) * drawScale
                            )
                        )
                    }
                    dragBox?.let { box ->
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(box.left * drawScale, box.top * drawScale),
                            size = Size(
                                (box.right - box.left) * drawScale,
                                (box.bottom - box.top) * drawScale
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { boxes = boxes.dropLast(1) },
                enabled = boxes.isNotEmpty()
            ) { Text("Undo") }
            TextButton(
                onClick = { boxes = emptyList() },
                enabled = boxes.isNotEmpty()
            ) { Text("Clear all") }
        }
        Text(
            if (boxes.isEmpty()) "No boxes drawn yet." else "${boxes.size} box(es) will be blacked out.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (error != null && bitmap != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(26.dp))
    }
}
