package app.locker.vault.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Export options that do not need a transcoder. The video stream is left untouched (lossless
 * remux matters more than a size slider), but its audio track can be lifted out into an M4A so a
 * recorded lecture or meeting can be kept as a voice memo.
 *
 * Full resolution/bitrate re-encoding would need MediaCodec transcoding; that is deliberately not
 * attempted here because a bad transcode is worse than no transcode.
 */
object MediaExport {
    private const val MAX_SAMPLES = 5_000_000

    /** A safe display name for an extracted audio track. */
    fun audioFileName(title: String): String {
        val base = title
            .substringBeforeLast('.', title)
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifBlank { "audio" }
            .take(60)
        return if (base.endsWith(".m4a", ignoreCase = true)) base else "$base.m4a"
    }

    /**
     * Copies the first audio track of [input] into [output] without re-encoding. Returns false
     * when the file has no audio track or the muxer rejects the stream.
     */
    suspend fun extractAudio(input: File, output: File): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext false

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val target = muxer.addTrack(format)
            muxer.start()

            val maxSize = format.getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
            val buffer = ByteBuffer.allocate(maxSize.coerceAtLeast(64 * 1024))
            val info = MediaCodec.BufferInfo()
            var firstPresentationTime = -1L
            var written = 0

            while (written < MAX_SAMPLES) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (firstPresentationTime < 0) firstPresentationTime = extractor.sampleTime
                info.offset = 0
                info.size = size
                info.presentationTimeUs = (extractor.sampleTime - firstPresentationTime).coerceAtLeast(0L)
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(target, buffer, info)
                extractor.advance()
                written++
            }
            return@withContext written > 0
        } catch (_: Throwable) {
            output.delete()
            false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
