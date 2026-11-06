package app.locker.vault.ui.screens.viewer

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.locker.vault.data.Mp4Trimmer
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    itemId: String,
    onBack: () -> Unit
) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val item = remember(itemId, items) { items.firstOrNull { it.id == itemId } }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(app.playback.speed(itemId)) }
    var showTrim by remember { mutableStateOf(false) }
    var trimming by remember { mutableStateOf(false) }
    var trimStart by remember { mutableStateOf(0f) }
    var trimEnd by remember { mutableStateOf(0f) }

    LaunchedEffect(itemId) {
        val current = item
        if (current == null) {
            error = "This video isn't available."
            return@LaunchedEffect
        }
        val decrypted = withContext(Dispatchers.IO) {
            runCatching { app.repository.decryptToShareCache(current) }.getOrNull()
        }
        if (decrypted == null) {
            error = "Could not decrypt this video."
            return@LaunchedEffect
        }
        file = decrypted
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            decrypted
        )
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            setPlaybackSpeed(speed)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        durationMs = this@apply.duration.coerceAtLeast(0)
                        if (trimEnd <= 0f) {
                            trimEnd = (durationMs / 1000f).coerceAtLeast(1f)
                        }
                        val saved = app.playback.position(itemId)
                        if (saved in 1_000 until (durationMs - 2_000)) {
                            seekTo(saved)
                        }
                    }
                    if (state == Player.STATE_ENDED) {
                        app.playback.clear(itemId)
                    }
                }
            })
            prepare()
            playWhenReady = true
        }
        player = exo
        while (true) {
            positionMs = exo.currentPosition
            delay(500)
        }
    }

    DisposableEffect(itemId) {
        onDispose {
            player?.let { exo ->
                if (exo.duration > 0 && exo.currentPosition < exo.duration - 2_000) {
                    app.playback.save(itemId, exo.currentPosition, speed)
                }
                exo.release()
            }
            file?.delete()
        }
    }

    fun changeSpeed(value: Float) {
        speed = value
        player?.setPlaybackSpeed(value)
    }

    fun saveTrim() {
        val current = item ?: return
        val input = file ?: return
        if (trimming) return
        val startMs = (trimStart * 1000f).toLong()
        val endMs = (trimEnd * 1000f).toLong()
        if (endMs - startMs < 1_000L) {
            android.widget.Toast.makeText(
                context,
                "Pick a clip of at least one second",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        trimming = true
        scope.launch {
            val output = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
            val ok = withContext(Dispatchers.IO) {
                Mp4Trimmer.trim(input, output, startMs, endMs)
            }
            if (!ok) {
                output.delete()
                trimming = false
                android.widget.Toast.makeText(
                    context,
                    "Could not trim this video",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                output
            )
            val result = app.repository.importFile(
                uri = uri,
                type = VaultType.VIDEO,
                title = "${current.title} (clip)",
                collectionIds = current.collectionIds
            )
            output.delete()
            trimming = false
            showTrim = false
            android.widget.Toast.makeText(
                context,
                result.fold(
                    { "Trimmed clip saved to the vault" },
                    { it.message ?: "Could not save the clip" }
                ),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (player != null) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (error == null) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    Text(
                        error ?: "Could not open video",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 36.dp, start = 12.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        if (player != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        val speeds = listOf(0.5f, 1f, 1.5f, 2f)
                        val next = speeds[(speeds.indexOf(speed).coerceAtLeast(0) + 1) % speeds.size]
                        changeSpeed(next)
                    },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Text(
                        if (speed == 1f) "1×" else "${speed}×",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    onClick = {
                        trimStart = 0f
                        trimEnd = (durationMs / 1000f).coerceAtLeast(1f)
                        player?.pause()
                        showTrim = true
                    },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ContentCut,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Trim",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    if (showTrim) {
        ModalBottomSheet(
            onDismissRequest = { if (!trimming) showTrim = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 22.dp)
            ) {
                Text(
                    "Trim video",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A clipped copy is saved to the vault. The original stays untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Start · ${clock(trimStart)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = trimStart.coerceIn(0f, (trimEnd - 1f).coerceAtLeast(0f)),
                    onValueChange = { trimStart = it },
                    valueRange = 0f..(durationMs / 1000f).coerceAtLeast(1f)
                )
                Text(
                    "End · ${clock(trimEnd)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = trimEnd.coerceIn(trimStart + 1f, (durationMs / 1000f).coerceAtLeast(2f)),
                    onValueChange = { trimEnd = it },
                    valueRange = 1f..(durationMs / 1000f).coerceAtLeast(2f)
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (!trimming) showTrim = false }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { saveTrim() }, enabled = !trimming) {
                        if (trimming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save clip")
                        }
                    }
                }
            }
        }
    }
}

private fun clock(seconds: Float): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
