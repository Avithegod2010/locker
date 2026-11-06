package app.locker.vault.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless MP4 clipping using the platform extractor and muxer. The clip is remuxed without
 * re-encoding, so it is fast and does not degrade quality. Timestamps are preserved, which
 * means the resulting clip starts at its original presentation time; players handle this.
 */
object Mp4Trimmer {
    fun trim(input: File, output: File, startMs: Long, endMs: Long): Boolean {
        if (endMs <= startMs + 250L) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(input.absolutePath)
            var videoTrack = -1
            var audioTrack = -1
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                when {
                    mime.startsWith("video/") && videoTrack < 0 -> videoTrack = index
                    mime.startsWith("audio/") && audioTrack < 0 -> audioTrack = index
                }
            }
            if (videoTrack < 0) return false

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()
            fun addTrack(track: Int) {
                extractor.selectTrack(track)
                trackMap[track] = muxer.addTrack(extractor.getTrackFormat(track))
            }
            addTrack(videoTrack)
            if (audioTrack >= 0) addTrack(audioTrack)
            muxer.setOrientationHint(videoRotation(input))
            muxer.start()
            started = true

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val timeUs = extractor.sampleTime
                if (timeUs > endUs) break
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val muxTrack = trackMap[track]
                if (muxTrack != null && timeUs >= startUs) {
                    info.set(0, size, timeUs, extractor.sampleFlags)
                    muxer.writeSampleData(muxTrack, buffer, info)
                }
                extractor.advance()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun videoRotation(file: File): Int = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        } finally {
            retriever.release()
        }
    }.getOrDefault(0)
}
