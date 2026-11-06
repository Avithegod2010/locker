package app.locker.vault.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun ImageEditorScreen(itemId: String, onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val item = remember(itemId, items) { items.firstOrNull { it.id == itemId } }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var redactMode by remember { mutableStateOf(true) }
    val redactions = remember { mutableStateListOf<Rect>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(itemId) {
        val current = item
        if (current == null) {
            error = "This image isn't available."
            loading = false
            return@LaunchedEffect
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { app.repository.readImage(current) }.getOrNull()
        }
        bitmap = bytes?.let {
            decodeSampledBitmap(it, 2400, 2400)
        }
        loading = false
        if (bitmap == null) error = "Could not decode this image."
    }

    DisposableEffect(Unit) {
        onDispose { bitmap?.recycle() }
    }

    fun rotate() {
        val current = bitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        bitmap = rotated
        redactions.clear()
    }

    fun save() {
        val current = bitmap ?: return
        if (saving) return
        saving = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = Bitmap.createBitmap(
                        current.width,
                        current.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(output)
                    canvas.drawBitmap(current, 0f, 0f, null)
                    val paint = Paint().apply { color = Color.BLACK }
                    redactions.forEach { rect ->
                        canvas.drawRect(
                            rect.left * current.width,
                            rect.top * current.height,
                            rect.right * current.width,
                            rect.bottom * current.height,
                            paint
                        )
                    }
                    val dir = File(context.cacheDir, "edited").apply { mkdirs() }
                    val file = File(dir, "edit_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { stream ->
                        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                    }
                    output.recycle()
                    file
                }
            }
            result.onSuccess { file ->
                val imported = app.repository.importFile(
                    uri = Uri.fromFile(file),
                    type = VaultType.PHOTO,
                    title = "${item?.title ?: "Image"} (edited)",
                    collectionIds = item?.collectionIds ?: emptyList()
                )
                file.delete()
                saving = false
                imported.onSuccess { onBack() }
                    .onFailure { error = it.message ?: "Could not save the edited image" }
            }.onFailure {
                saving = false
                error = it.message ?: "Could not save the edited image"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        BackTopBar(title = "Edit image", onBack = onBack)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }
        val current = bitmap
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Could not open image", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(current.width.toFloat() / current.height.toFloat())
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(current, redactMode) {
                    if (!redactMode) return@pointerInput
                    detectDragGestures(
                        onDragStart = {
                            dragStart = it
                            dragCurrent = it
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragCurrent = change.position
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragCurrent
                            if (start != null && end != null) {
                                val left = minOf(start.x, end.x) / size.width
                                val right = maxOf(start.x, end.x) / size.width
                                val top = minOf(start.y, end.y) / size.height
                                val bottom = maxOf(start.y, end.y) / size.height
                                if (right - left > 0.01f && bottom - top > 0.01f) {
                                    redactions += Rect(left, top, right, bottom)
                                }
                            }
                            dragStart = null
                            dragCurrent = null
                        },
                        onDragCancel = {
                            dragStart = null
                            dragCurrent = null
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = current.asImageBitmap(),
                    dstSize = androidx.compose.ui.unit.IntSize(
                        size.width.toInt(),
                        size.height.toInt()
                    )
                )
                val paint = androidx.compose.ui.graphics.Paint().apply {
                    color = ComposeColor.Black
                }
                redactions.forEach { rect ->
                    drawRect(
                        color = ComposeColor.Black,
                        topLeft = Offset(rect.left * size.width, rect.top * size.height),
                        size = Size(
                            (rect.right - rect.left) * size.width,
                            (rect.bottom - rect.top) * size.height
                        )
                    )
                }
                val start = dragStart
                val end = dragCurrent
                if (start != null && end != null) {
                    drawRect(
                        color = ComposeColor.Black.copy(alpha = 0.75f),
                        topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        size = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { redactMode = !redactMode },
                shape = RoundedCornerShape(18.dp),
                color = if (redactMode) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Redact", style = MaterialTheme.typography.labelLarge)
                }
            }
            Surface(
                onClick = { rotate() },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Rotate", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { save() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Save,
                            contentDescription = "Save edited copy",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        Text(
            "Drag on the photo to draw black redactions. Rotate first if needed. The result is saved as a new encrypted copy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        if (error != null) {
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
