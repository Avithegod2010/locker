package app.locker.vault.ui.screens.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.locker.vault.core.Formatting
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

private enum class CameraMode { PHOTO, VIDEO }

@Composable
fun SecureCameraScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var recordElapsed by remember { mutableStateOf(0L) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        cameraGranted = allowed
        if (!allowed) error = "Camera permission is needed to capture privately."
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed -> audioGranted = allowed }

    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Microphone is requested the first time the user switches to video mode. Denying it
    // simply records silent video; nothing is ever uploaded either way.
    LaunchedEffect(mode) {
        if (mode == CameraMode.VIDEO && !audioGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { activeRecording?.stop() }
            activeRecording = null
        }
    }

    LaunchedEffect(cameraGranted, mode) {
        if (!cameraGranted) return@LaunchedEffect
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
        runCatching {
            provider.unbindAll()
            when (mode) {
                CameraMode.PHOTO -> {
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    imageCapture = capture
                }
                CameraMode.VIDEO -> {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                Quality.HD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                            )
                        )
                        .build()
                    val capture = VideoCapture.withOutput(recorder)
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    videoCapture = capture
                }
            }
            cameraReady = true
            error = null
        }.onFailure {
            error = "Could not open the camera: ${it.message ?: "unknown error"}"
            cameraReady = false
        }
    }

    fun captureDir(): File = File(context.cacheDir, "camera").apply { mkdirs() }

    fun saveCapture(file: File, type: VaultType, title: String) {
        // Imports run on the application scope so a quick dismiss cannot abort a save.
        app.appScope.launch {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            val result = app.repository.importFile(
                uri = uri,
                type = type,
                title = title,
                collectionIds = emptyList()
            )
            file.delete()
            capturing = false
            result.onSuccess { onBack() }
                .onFailure { error = it.message ?: "Could not save the capture" }
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (capturing || activeRecording != null || mode != CameraMode.PHOTO) return
        capturing = true
        error = null
        val file = File(captureDir(), "secure_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    saveCapture(file, VaultType.PHOTO, Formatting.photoTitle())
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    capturing = false
                    error = "Could not take the photo: ${exception.message ?: "unknown error"}"
                }
            }
        )
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        if (capturing || activeRecording != null || mode != CameraMode.VIDEO) return
        capturing = true
        error = null
        val file = File(captureDir(), "secure_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        recordStartMs = System.currentTimeMillis()
        recordElapsed = 0
        try {
            val pending = capture.output.prepareRecording(context, options)
            val started = (if (audioGranted) pending.withAudioEnabled() else pending)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        activeRecording = null
                        recordStartMs = 0
                        if (event.hasError()) {
                            file.delete()
                            capturing = false
                            error = "Recording failed: ${event.cause?.message ?: "try again"}"
                        } else {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                .format(java.util.Date())
                            saveCapture(file, VaultType.VIDEO, "Video_$stamp")
                        }
                    }
                }
            activeRecording = started
        } catch (t: Throwable) {
            file.delete()
            capturing = false
            recordStartMs = 0
            error = "Could not record: ${t.message ?: "unknown error"}"
        }
    }

    fun stopRecording() {
        runCatching { activeRecording?.stop() }
    }

    LaunchedEffect(recordStartMs) {
        while (recordStartMs > 0) {
            recordElapsed = (System.currentTimeMillis() - recordStartMs) / 1000
            delay(500)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraGranted) {
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
                    onClick = {
                        if (activeRecording != null) stopRecording()
                        onBack()
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (activeRecording == null) {
                    Row(
                        Modifier
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(5.dp)
                    ) {
                        PhotoVideoChip(
                            text = "Photo",
                            selected = mode == CameraMode.PHOTO,
                            onClick = {
                                if (!capturing) {
                                    mode = CameraMode.PHOTO
                                    imageCapture = null
                                    videoCapture = null
                                    cameraReady = false
                                }
                            }
                        )
                        PhotoVideoChip(
                            text = "Video",
                            selected = mode == CameraMode.VIDEO,
                            onClick = {
                                if (!capturing) {
                                    mode = CameraMode.VIDEO
                                    imageCapture = null
                                    videoCapture = null
                                    cameraReady = false
                                }
                            }
                        )
                    }
                }
            }

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (error != null || !cameraGranted) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                error ?: "Camera permission is required.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            if (!cameraGranted) {
                                TextButton(onClick = {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }) { Text("Grant permission") }
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }) { Text("Open settings") }
                            }
                        }
                    }
                }
            }

            if (activeRecording != null) {
                Row(
                    Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        tint = Color(0xFFFF5A5A),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        String.format(Locale.US, "%02d:%02d", recordElapsed / 60, recordElapsed % 60),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                "Captured straight into the encrypted vault. No gallery copy is created.",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
            )
            Spacer(Modifier.height(18.dp))

            if (cameraGranted) {
                Row(Modifier.align(Alignment.CenterHorizontally)) {
                    Surface(
                        onClick = {
                            when {
                                mode == CameraMode.PHOTO -> takePhoto()
                                activeRecording == null -> startRecording()
                                else -> stopRecording()
                            }
                        },
                        shape = CircleShape,
                        color = when {
                            activeRecording != null -> Color(0xFFFF5A5A)
                            cameraReady -> Color.White.copy(alpha = 0.95f)
                            else -> Color.White.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(82.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (capturing && activeRecording == null && mode == CameraMode.PHOTO) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(30.dp)
                                )
                            } else if (activeRecording != null) {
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = "Stop recording",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            } else {
                                Icon(
                                    if (mode == CameraMode.PHOTO) Icons.Rounded.PhotoCamera
                                    else Icons.Rounded.Videocam,
                                    contentDescription = if (mode == CameraMode.PHOTO) {
                                        "Take photo"
                                    } else "Record video",
                                    tint = Color.Black,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhotoVideoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
