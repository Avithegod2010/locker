package app.locker.vault.data

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max

/**
 * Looks for a page shape in the live camera feed.
 *
 * The analyser reads only the luminance plane and samples it down to roughly [TARGET_WIDTH]
 * pixels before running [PageQuadDetector]. That is enough for corner detection, keeps the
 * per-frame cost tiny, and never touches the captured still.
 */
class PageAnalyzer(
    private val onFrame: (quad: PageQuad?, frameWidth: Int) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) {
                onFrame(null, 0)
                return
            }
            val plane = image.planes.firstOrNull()
            if (plane == null) {
                onFrame(null, 0)
                return
            }
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            if (rowStride <= 0 || pixelStride <= 0) {
                onFrame(null, 0)
                return
            }
            val step = max(1, image.width / TARGET_WIDTH)
            val width = image.width / step
            val height = image.height / step
            if (width < 40 || height < 40) {
                onFrame(null, 0)
                return
            }

            val pixels = IntArray(width * height)
            var index = 0
            for (y in 0 until height) {
                val rowStart = y * step * rowStride
                for (x in 0 until width) {
                    val offset = rowStart + x * step * pixelStride
                    val luma = if (offset < buffer.limit()) {
                        buffer.get(offset).toInt() and 0xFF
                    } else {
                        0
                    }
                    pixels[index++] = GRAY or (luma shl 16) or (luma shl 8) or luma
                }
            }
            onFrame(PageQuadDetector.detect(pixels, width, height), width)
        } catch (_: Throwable) {
            onFrame(null, 0)
        } finally {
            image.close()
        }
    }

    companion object {
        private const val TARGET_WIDTH = 200
        private const val GRAY = 0xFF shl 24
    }
}
