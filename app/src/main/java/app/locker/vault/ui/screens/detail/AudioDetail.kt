package app.locker.vault.ui.screens.detail

import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.locker.vault.data.VaultItem
import app.locker.vault.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Waveform player for encrypted voice memos. */
@Composable
fun AudioDetail(item: VaultItem) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }
    var error by remember { mutableStateOf<String?>(null) }

    val peaks = remember(item.id) {
        val raw = item.fields.firstOrNull { it.label == "waveform" }?.value
            ?.split(',')
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: List(48) { 0.2f }
        val max = raw.maxOrNull()?.coerceAtLeast(0.0001f) ?: 1f
        if (max > 1f) raw.map { it / max } else raw
    }

    LaunchedEffect(item.id) {
        val decrypted = withContext(Dispatchers.IO) {
            runCatching { app.repository.decryptToShareCache(item) }.getOrNull()
        }
        if (decrypted == null) {
            error = "Could not decrypt this recording."
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
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) durationMs = this@apply.duration.coerceAtLeast(0)
                    if (state == Player.STATE_ENDED) {
                        playing = false
                        positionMs = 0
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            })
        }
        player = exo
        while (true) {
            positionMs = exo.currentPosition
            delay(300)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            file?.delete()
        }
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            if (error != null) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        val exo = player ?: return@Surface
                        if (exo.isPlaying) exo.pause() else exo.play()
                    },
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    val count = peaks.size
                    val gap = 3.dp.toPx()
                    val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
                    peaks.forEachIndexed { index, peak ->
                        val height = (peak.coerceIn(0.05f, 1f)) * size.height
                        val x = index * (barWidth + gap)
                        val played = index.toFloat() / count <= progress
                        drawRoundRect(
                            color = if (played) accent else idle,
                            topLeft = Offset(x, (size.height - height) / 2f),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = if (durationMs > 0) positionMs.toFloat().coerceIn(0f, durationMs.toFloat()) else 0f,
                onValueChange = { value ->
                    positionMs = value.toLong()
                    player?.seekTo(value.toLong())
                },
                valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat()
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${format(positionMs)} / ${format(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 1f, 1.5f, 2f).forEach { value ->
                        Surface(
                            onClick = {
                                speed = value
                                player?.setPlaybackSpeed(value)
                            },
                            shape = CircleShape,
                            color = if (speed == value) {
                                accent.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (speed == value) accent else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                if (value == 1f) "1×" else "${value}×",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (speed == value) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun format(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
