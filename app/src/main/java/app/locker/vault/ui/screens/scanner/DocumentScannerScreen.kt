package app.locker.vault.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.locker.vault.data.DocumentScan
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

private data class ScanPage(val file: File, val thumb: Bitmap)

/** Multi-page document scanner: captures, auto-trims borders, and stores a PDF in the vault. */
@Composable
fun DocumentScannerScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pages by remember { mutableStateOf<List<ScanPage>>(emptyList()) }
    var capturing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        if (!allowed) error = "Camera permission is required to scan pages."
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            pages.forEach { page ->
                page.file.delete()
                if (!page.thumb.isRecycled) page.thumb.recycle()
            }
        }
    }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val provider = runCatching {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener(
                        {
                            runCatching { future.get() }
                                .onSuccess { continuation.resume(it) }
                                .onFailure { continuation.resumeWith(Result.failure(it)) }
                        },
                        ContextCompat.getMainExecutor(context)
                    )
                }
            }
        }.getOrNull() ?: run {
            error = "This device could not start the camera."
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            imageCapture = capture
            cameraReady = true
            error = null
        }.onFailure {
            error = "Could not open the camera: ${it.message ?: "unknown error"}"
        }
    }

    fun capturePage() {
        val capture = imageCapture ?: return
        if (capturing || saving) return
        capturing = true
        error = null
        val raw = File(context.cacheDir, "scan_raw_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(raw).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val page = withContext(Dispatchers.IO) {
                            runCatching {
                                val bitmap = DocumentScan.loadPage(raw) ?: return@runCatching null
                                val encoded = File(
                                    context.cacheDir,
                                    "scan_page_${System.currentTimeMillis()}.jpg"
                                )
                                encoded.outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                                }
                                val thumbWidth = 180
                                val thumbHeight = (bitmap.height * thumbWidth.toFloat() / bitmap.width)
                                    .toInt().coerceAtLeast(1)
                                val thumb = Bitmap.createScaledBitmap(
                                    bitmap,
                                    thumbWidth,
                                    thumbHeight,
                                    true
                                )
                                bitmap.recycle()
                                ScanPage(encoded, thumb)
                            }.getOrNull()
                        }
                        raw.delete()
                        if (page != null) {
                            pages = pages + page
                        } else {
                            error = "Could not process that page."
                        }
                        capturing = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    raw.delete()
                    capturing = false
                    error = "Could not capture the page: ${exception.message ?: "unknown error"}"
                }
            }
        )
    }

    fun savePdf() {
        if (pages.isEmpty() || saving) return
        saving = true
        error = null
        val snapshot = pages
        scope.launch {
            val output = File(context.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
            val bitmaps = withContext(Dispatchers.IO) {
                snapshot.mapNotNull { page ->
                    BitmapFactory.decodeFile(page.file.absolutePath)
                }
            }
            val ok = withContext(Dispatchers.IO) {
                DocumentScan.createPdf(bitmaps, output)
            }
            if (!ok) {
                output.delete()
                saving = false
                error = "Could not build the PDF."
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                output
            )
            val title = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date())
            val result = app.repository.importFile(
                uri = uri,
                type = VaultType.FILE,
                title = title,
                collectionIds = emptyList()
            )
            output.delete()
            snapshot.forEach { page ->
                page.file.delete()
                if (!page.thumb.isRecycled) page.thumb.recycle()
            }
            pages = emptyList()
            saving = false
            result.onSuccess { onBack() }
                .onFailure { error = it.message ?: "Could not save the PDF" }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (granted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Scan document",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Borders are trimmed automatically. Pages stay on this device.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Text(
                        "${pages.size} page(s)",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            error?.let { message ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (pages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
                ) {
                    itemsIndexed(pages) { index, page ->
                        Box {
                            Image(
                                bitmap = page.thumb.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 64.dp, height = 82.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Surface(
                                onClick = {
                                    page.file.delete()
                                    if (!page.thumb.isRecycled) page.thumb.recycle()
                                    pages = pages.filterNot { it === page }
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(22.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove page",
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (!saving) {
                            pages.forEach { page ->
                                page.file.delete()
                                if (!page.thumb.isRecycled) page.thumb.recycle()
                            }
                            pages = emptyList()
                        }
                    },
                    enabled = pages.isNotEmpty() && !saving
                ) {
                    Text("Clear", color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.width(14.dp))
                Surface(
                    onClick = { capturePage() },
                    shape = CircleShape,
                    color = if (cameraReady) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(82.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (capturing || saving) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.PhotoCamera,
                                contentDescription = "Capture page",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                TextButton(
                    onClick = { savePdf() },
                    enabled = pages.isNotEmpty() && !saving
                ) {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        tint = if (pages.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Save PDF",
                        color = if (pages.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White
                    )
                }
            }
        }
    }
}
