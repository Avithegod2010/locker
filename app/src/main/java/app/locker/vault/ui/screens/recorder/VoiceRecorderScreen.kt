package app.locker.vault.ui.screens.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.locker.vault.data.CustomField
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun VoiceRecorderScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var accumulatedMs by remember { mutableStateOf(0L) }
    var segmentStartMs by remember { mutableStateOf(0L) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var peaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        if (!allowed) error = "Microphone permission is required to record a memo."
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
        }
    }

    fun startRecording() {
        if (recording || saving) return
        error = null
        val file = File(context.cacheDir, "memo_${System.currentTimeMillis()}.m4a")
        try {
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(96_000)
            created.setAudioSamplingRate(44_100)
            created.setOutputFile(file.absolutePath)
            created.prepare()
            created.start()
            recorder = created
            activeFile = file
            pendingFile = null
            peaks = emptyList()
            elapsedMs = 0
            accumulatedMs = 0
            segmentStartMs = System.currentTimeMillis()
            paused = false
            recording = true
        } catch (t: Throwable) {
            file.delete()
            error = "Could not start recording: ${t.message ?: "unknown error"}"
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        paused = false
        val current = recorder
        recorder = null
        val file = try {
            current?.stop()
            current?.release()
            activeFile
        } catch (t: Throwable) {
            activeFile?.delete()
            null
        }
        runCatching { current?.release() }
        activeFile = null
        if (file == null || !file.exists()) {
            error = "That recording was too short to save."
            return
        }
        pendingFile = file
    }

    LaunchedEffect(recording, paused) {
        if (!recording || paused) return@LaunchedEffect
        while (recording && !paused) {
            elapsedMs = accumulatedMs + (System.currentTimeMillis() - segmentStartMs)
            val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
            peaks = (peaks + normalized).takeLast(220)
            delay(90)
        }
    }

    fun pauseRecording() {
        if (!recording || paused) return
        runCatching { recorder?.pause() }
        accumulatedMs += System.currentTimeMillis() - segmentStartMs
        elapsedMs = accumulatedMs
        paused = true
    }

    fun resumeRecording() {
        if (!recording || !paused) return
        runCatching { recorder?.resume() }
        segmentStartMs = System.currentTimeMillis()
        paused = false
    }

    fun discard() {
        pendingFile?.delete()
        pendingFile = null
        peaks = emptyList()
        elapsedMs = 0
    }

    fun save() {
        val file = pendingFile ?: return
        if (saving) return
        saving = true
        error = null
        val capturedPeaks = peaks
        scope.launch {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            val title = "Voice_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date(file.lastModified()))
            val result = app.repository.importFile(
                uri = uri,
                type = VaultType.FILE,
                title = title,
                collectionIds = emptyList()
            )
            result.onSuccess { item ->
                val waveform = capturedPeaks.map { (it * 100).toInt() }
                    .joinToString(",")
                if (waveform.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        app.repository.updateItem(
                            item.copy(fields = item.fields + CustomField("waveform", waveform))
                        )
                    }
                }
                file.delete()
                saving = false
                pendingFile = null
                onBack()
            }.onFailure {
                saving = false
                file.delete()
                pendingFile = null
                error = it.message ?: "Could not save the recording"
            }
        }
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
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Voice memo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Encrypted on this device. No audio ever leaves it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                clock(elapsedMs),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WaveformBars(
                        peaks = if (peaks.isEmpty()) List(48) { 0.06f } else peaks,
                        progress = 1f,
                        active = MaterialTheme.colorScheme.primary,
                        idle = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    error != null -> error ?: ""
                    pendingFile != null -> "Recording ready — save it to your vault or discard it."
                    recording && paused -> "Paused — tap play beside the stop button to resume."
                    recording -> "Recording… tap the pause button for a break, stop when done."
                    else -> "Tap the microphone to start recording."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pendingFile != null && !saving) {
                IconButton(onClick = { discard() }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Discard recording",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
            if (recording && !saving) {
                Surface(
                    onClick = { if (paused) resumeRecording() else pauseRecording() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (paused) "Resume recording" else "Pause recording",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
            }
            Surface(
                onClick = {
                    when {
                        saving -> Unit
                        recording -> stopRecording()
                        pendingFile != null -> save()
                        granted -> startRecording()
                        else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                shape = CircleShape,
                color = when {
                    recording -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(86.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (saving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Icon(
                            if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (recording) "Stop recording" else "Start recording",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            if (pendingFile != null && !saving) {
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = { save() }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun WaveformBars(
    peaks: List<Float>,
    progress: Float,
    active: Color,
    idle: Color
) {
    Canvas(Modifier.fillMaxSize()) {
        val count = peaks.size
        if (count == 0) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val max = peaks.maxOrNull() ?: 1f
        peaks.forEachIndexed { index, peak ->
            val normalized = max(0.06f, min(1f, peak / max))
            val height = normalized * size.height
            drawRoundRect(
                color = if (index.toFloat() / count <= progress) active else idle,
                topLeft = Offset(index * (barWidth + gap), (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
