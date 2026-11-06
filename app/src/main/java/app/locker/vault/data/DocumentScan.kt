package app.locker.vault.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.min

/**
 * Document scanning helpers: rough border detection for auto-cropping a photographed page,
 * EXIF-correct decoding, and building a multi-page PDF entirely on-device.
 */
object DocumentScan {
    private const val MAX_SIDE = 2200

    fun loadPage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val rotated = applyExifRotation(file, decoded)
        return autoTrim(rotated)
    }

    /**
     * Crops the uniform border around a photographed page. The background colour is estimated
     * from the corners; rows/columns that are almost entirely background are trimmed away.
     */
    fun autoTrim(bitmap: Bitmap): Bitmap {
        if (bitmap.width < 80 || bitmap.height < 80) return bitmap
        val sampleTarget = 260
        val ratio = min(1f, sampleTarget.toFloat() / maxOf(bitmap.width, bitmap.height))
        val small = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        val width = small.width
        val height = small.height
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()

        // Background = average of the four corner patches.
        val corner = IntArray(4)
        corner[0] = pixels[0]
        corner[1] = pixels[width - 1]
        corner[2] = pixels[(height - 1) * width]
        corner[3] = pixels[height * width - 1]
        val bgR = corner.sumOf { Color.red(it) } / 4
        val bgG = corner.sumOf { Color.green(it) } / 4
        val bgB = corner.sumOf { Color.blue(it) } / 4

        fun isContent(index: Int): Boolean {
            val color = pixels[index]
            val distance = kotlin.math.abs(Color.red(color) - bgR) +
                kotlin.math.abs(Color.green(color) - bgG) +
                kotlin.math.abs(Color.blue(color) - bgB)
            return distance > 90
        }

        fun rowHasContent(y: Int): Boolean {
            var hits = 0
            for (x in 0 until width) {
                if (isContent(y * width + x)) {
                    hits++
                    if (hits > width * 0.02f) return true
                }
            }
            return false
        }

        fun columnHasContent(x: Int): Boolean {
            var hits = 0
            for (y in 0 until height) {
                if (isContent(y * width + x)) {
                    hits++
                    if (hits > height * 0.02f) return true
                }
            }
            return false
        }

        var top = 0
        while (top < height / 2 && !rowHasContent(top)) top++
        var bottom = height - 1
        while (bottom > top && !rowHasContent(bottom)) bottom--
        var left = 0
        while (left < width / 2 && !columnHasContent(left)) left++
        var right = width - 1
        while (right > left && !columnHasContent(right)) right--

        if (right - left < width * 0.35f || bottom - top < height * 0.35f) {
            return bitmap // detection failed; keep the full page
        }
        val scaleX = bitmap.width.toFloat() / width
        val scaleY = bitmap.height.toFloat() / height
        val margin = (min(width, height) * 0.01f).toInt()
        val cropLeft = ((left - margin).coerceAtLeast(0) * scaleX).toInt()
        val cropTop = ((top - margin).coerceAtLeast(0) * scaleY).toInt()
        val cropRight = ((right + margin + 1).coerceAtMost(width) * scaleX).toInt()
        val cropBottom = ((bottom + margin + 1).coerceAtMost(height) * scaleY).toInt()
        val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)
        val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    fun createPdf(pages: List<Bitmap>, output: File): Boolean {
        if (pages.isEmpty()) return false
        val document = PdfDocument()
        return try {
            pages.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = document.startPage(pageInfo)
                drawPage(page.canvas, bitmap)
                document.finishPage(page)
            }
            output.outputStream().use { document.writeTo(it) }
            true
        } catch (_: Exception) {
            false
        } finally {
            document.close()
            pages.forEach { it.recycle() }
        }
    }

    private fun drawPage(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawColor(Color.WHITE)
        val margin = 20f
        val maxWidth = 595f - margin * 2
        val maxHeight = 842f - margin * 2
        val scale = min(maxWidth / bitmap.width, maxHeight / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (595f - width) / 2f
        val top = (842f - height) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + width, top + height),
            paint
        )
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
