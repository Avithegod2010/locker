package app.locker.vault.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Re-encodes a recorded video at a smaller size so it is easier to share.
 *
 * The pipeline is deliberately two-step: the video is transcoded to a video-only file, then that
 * file is remuxed with the original audio track. MediaMuxer requires every track to be added
 * before it starts, and the encoder's output format is only known after the first frame has been
 * drained, so a single-pass mux would have to buffer the whole encode. The two-step version keeps
 * memory flat and lets the audio stay untouched.
 *
 * Anything unexpected returns false and deletes the partial output; the caller keeps the original.
 */
object VideoTranscode {
    data class Preset(val id: String, val label: String, val maxDimension: Int, val bitrate: Int)

    val presets: List<Preset> = listOf(
        Preset(id = "720p", label = "Smaller (720p)", maxDimension = 1280, bitrate = 2_800_000),
        Preset(id = "480p", label = "Smallest (480p)", maxDimension = 854, bitrate = 1_100_000)
    )

    fun presetById(id: String): Preset? = presets.firstOrNull { it.id == id }

    /**
     * Scales [width] x [height] down so the longest side fits [maxDimension], preserving the
     * aspect ratio and keeping both sides even (H.264 requires it). Never upscales.
     */
    fun targetSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val longest = maxOf(width, height)
        val scale = if (longest <= maxDimension) 1f else maxDimension.toFloat() / longest
        val targetWidth = ((width * scale).roundToInt()).coerceAtLeast(2) and 0xFFFFFFFE.toInt()
        val targetHeight = ((height * scale).roundToInt()).coerceAtLeast(2) and 0xFFFFFFFE.toInt()
        return targetWidth to targetHeight
    }

    /** A conservative bitrate for the target size, clamped to something H.264 encoders accept. */
    fun bitrateFor(width: Int, height: Int, framesPerSecond: Int = 30): Int {
        val fps = framesPerSecond.coerceIn(15, 60)
        val estimate = (width.toLong() * height * fps * 0.07).toInt()
        return estimate.coerceIn(600_000, 6_000_000)
    }

    /** Names the exported copy, e.g. "Holiday clip (720p).mp4". */
    fun outputName(title: String, presetId: String): String {
        val base = title
            .substringBeforeLast('.', title)
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .trim()
            .ifBlank { "video" }
            .take(60)
        return "$base ($presetId).mp4"
    }

    suspend fun export(input: File, output: File, preset: Preset): Boolean =
        withContext(Dispatchers.IO) {
            val videoOnly = File(output.parentFile, "${output.nameWithoutExtension}.video.mp4")
            try {
                if (!transcodeVideo(input, videoOnly, preset)) return@withContext false
                if (!remuxWithOriginalAudio(input, videoOnly, output)) return@withContext false
                output.exists() && output.length() > 0
            } catch (_: Throwable) {
                output.delete()
                false
            } finally {
                videoOnly.delete()
            }
        }

    // --- step 1: video only, decoded to the encoder's surface ------------------------------

    private fun transcodeVideo(input: File, output: File, preset: Preset): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            extractor.selectTrack(track)
            val source = extractor.getTrackFormat(track)
            val mime = source.getString(MediaFormat.KEY_MIME) ?: return false
            val sourceWidth = source.getInteger(MediaFormat.KEY_WIDTH)
            val sourceHeight = source.getInteger(MediaFormat.KEY_HEIGHT)
            val (width, height) = targetSize(sourceWidth, sourceHeight, preset.maxDimension)
            if (width < 16 || height < 16) return false

            val fps = if (source.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                source.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(15, 60)
            } else {
                30
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val encodedFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, fps))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder.configure(encodedFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(source, surface, null, 0)
            decoder.start()

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerStarted = false
            var videoTrack = -1
            var frame = 0

            val info = MediaCodec.BufferInfo()
            var decoderDone = false
            var encoderDone = false
            var inputDone = false

            while (!encoderDone) {
                // Feed the decoder from the extractor.
                if (!decoderDone && !inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: return false
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                index, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                index, 0, size, extractor.sampleTime,
                                extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                // Drain the decoder; rendering to the encoder surface is what hands frames over.
                if (!decoderDone) {
                    when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                        else -> if (outIndex >= 0) {
                            val endOfStream =
                                info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            // true = render this frame to the surface
                            decoder.releaseOutputBuffer(outIndex, !endOfStream)
                            if (endOfStream) {
                                decoderDone = true
                                encoder.signalEndOfInputStream()
                            }
                        }
                    }
                }

                // Drain the encoder into the muxer.
                when (val outIndex = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) return false
                        videoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outIndex >= 0) {
                        val buffer = encoder.getOutputBuffer(outIndex) ?: return false
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0 && muxerStarted) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, buffer, info)
                            frame++
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                    }
                }
            }
            return frame > 0
        } catch (_: Throwable) {
            output.delete()
            return false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { extractor.release() }
        }
    }

    // --- step 2: keep the original audio, swap in the new video track -----------------------

    private fun remuxWithOriginalAudio(original: File, videoOnly: File, output: File): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoExtractor.setDataSource(videoOnly.absolutePath)
            audioExtractor.setDataSource(original.absolutePath)

            val videoTrack = (0 until videoExtractor.trackCount).firstOrNull {
                videoExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: return false
            val audioTrack = (0 until audioExtractor.trackCount).firstOrNull {
                audioExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideo = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxAudio = if (audioTrack != null) {
                muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            } else {
                -1
            }
            // Carry the source rotation over so portrait clips stay upright in other players.
            readRotation(original)?.let { rotation ->
                runCatching { muxer.setOrientationHint(rotation) }
            }
            muxer.start()

            videoExtractor.selectTrack(videoTrack)
            if (!copySamples(
                    videoExtractor,
                    muxer,
                    muxVideo,
                    videoExtractor.getTrackFormat(videoTrack)
                )
            ) return false
            if (audioTrack != null) {
                audioExtractor.selectTrack(audioTrack)
                if (!copySamples(
                        audioExtractor,
                        muxer,
                        muxAudio,
                        audioExtractor.getTrackFormat(audioTrack)
                    )
                ) return false
            }
            return true
        } catch (_: Throwable) {
            output.delete()
            return false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        format: MediaFormat
    ): Boolean {
        val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
        } else {
            1024 * 1024
        }
        val buffer = ByteBuffer.allocate(maxSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
        return true
    }

    private fun readRotation(file: File): Int? = runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
