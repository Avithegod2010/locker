package app.locker.vault.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device PDF surgery without a third-party library: extract pages, merge documents,
 * re-compress, and burn redaction boxes into a page.
 *
 * Every output page is re-rendered as an image and written into a fresh document. That is a
 * deliberate trade: the original text layer is discarded, so a redaction genuinely removes the
 * glyphs instead of painting over them, and merge/split cannot smuggle hidden PDF objects across.
 * The cost is that output pages are raster images rather than selectable text.
 */
object PdfTools {
    const val DEFAULT_RENDER_WIDTH = 1400
    const val COMPRESS_RENDER_WIDTH = 900
    private const val MAX_PAGES = 500

    fun pageCount(file: File): Int = withRenderer(file) { it.pageCount } ?: 0

    /** Renders one page at [widthPx], painting [boxes] solid black before returning it. */
    fun renderPage(
        file: File,
        pageIndex: Int,
        widthPx: Int = DEFAULT_RENDER_WIDTH,
        boxes: List<NormalizedRect> = emptyList(),
        forPrint: Boolean = false
    ): Bitmap? = withRenderer(file) { renderer ->
        if (pageIndex !in 0 until renderer.pageCount) return@withRenderer null
        renderPage(renderer, pageIndex, widthPx, boxes, forPrint)
    }

    suspend fun extractPages(
        source: File,
        pages: List<Int>,
        output: File,
        widthPx: Int = DEFAULT_RENDER_WIDTH
    ): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false
        withRenderer(source) { renderer ->
            val count = renderer.pageCount
            val selected = pages.filter { it in 1..count }.distinct().sorted()
            if (selected.isEmpty()) return@withRenderer false
            var position = 0
            buildPdf(output, selected.size) {
                val pageNumber = selected.getOrNull(position++) ?: return@buildPdf null
                renderPage(renderer, pageNumber - 1, widthPx)
            }
        } ?: false
    }

    suspend fun merge(
        sources: List<File>,
        output: File,
        widthPx: Int = DEFAULT_RENDER_WIDTH
    ): Boolean = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext false
        val parts = sources.filter { pageCount(it) > 0 }
        if (parts.size < 1) return@withContext false
        // Open one renderer at a time: a merged document can be large and the host is small.
        val total = parts.sumOf { pageCount(it) }
        if (total == 0 || total > MAX_PAGES) return@withContext false
        buildPdf(output, total) { overall ->
            var remaining = overall
            for (part in parts) {
                val count = pageCount(part)
                if (remaining < count) {
                    return@buildPdf withRenderer(part) { renderer ->
                        renderPage(renderer, remaining, widthPx)
                    }
                }
                remaining -= count
            }
            null
        }
    }

    suspend fun compress(
        source: File,
        output: File,
        widthPx: Int = COMPRESS_RENDER_WIDTH
    ): Boolean = withContext(Dispatchers.IO) {
        withRenderer(source) { renderer ->
            val count = renderer.pageCount.coerceAtMost(MAX_PAGES)
            if (count == 0) return@withRenderer false
            buildPdf(output, count) { index -> renderPage(renderer, index, widthPx) }
        } ?: false
    }

    /**
     * Burns [boxes] into one page and rebuilds the whole document. Every page is re-rendered, so
     * the redacted text is not recoverable from the output file.
     */
    suspend fun redactPage(
        source: File,
        pageNumber: Int,
        boxes: List<NormalizedRect>,
        output: File,
        widthPx: Int = DEFAULT_RENDER_WIDTH
    ): Boolean = withContext(Dispatchers.IO) {
        if (boxes.isEmpty()) return@withContext false
        withRenderer(source) { renderer ->
            val count = renderer.pageCount.coerceAtMost(MAX_PAGES)
            val target = pageNumber - 1
            if (target !in 0 until count) return@withRenderer false
            buildPdf(output, count) { index ->
                if (index == target) {
                    renderPage(renderer, index, widthPx, boxes, forPrint = true)
                } else {
                    renderPage(renderer, index, widthPx)
                }
            }
        } ?: false
    }

    private fun renderPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        widthPx: Int,
        boxes: List<NormalizedRect> = emptyList(),
        forPrint: Boolean = false
    ): Bitmap? = runCatching {
        renderer.openPage(pageIndex).use { page ->
            val width = widthPx.coerceIn(200, 3000)
            val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(
                bitmap,
                null,
                null,
                if (forPrint) PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                else PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            if (boxes.isNotEmpty()) {
                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                boxes.forEach { box ->
                    val pixels = PdfGeometry.toPixels(PdfGeometry.pad(box), width, height)
                    canvas.drawRect(
                        pixels[0].toFloat(),
                        pixels[1].toFloat(),
                        pixels[2].toFloat(),
                        pixels[3].toFloat(),
                        paint
                    )
                }
            }
            bitmap
        }
    }.getOrNull()

    /**
     * Writes pages produced lazily by [render] into a new document, recycling each bitmap as soon
     * as it has been drawn so a long document never has to fit in memory at once.
     */
    private fun buildPdf(output: File, pageCount: Int, render: (Int) -> Bitmap?): Boolean {
        if (pageCount <= 0) return false
        val document = PdfDocument()
        var written = 0
        return try {
            for (index in 0 until pageCount) {
                val bitmap = render(index) ?: continue
                val ratio = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
                val widthPt = 595
                val heightPt = (widthPt * ratio).toInt().coerceIn(1, 20_000)
                val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, written + 1).create()
                val page = document.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, widthPt.toFloat(), heightPt.toFloat()),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                )
                document.finishPage(page)
                written++
                bitmap.recycle()
            }
            if (written == 0) return false
            output.outputStream().use { document.writeTo(it) }
            true
        } catch (_: Throwable) {
            false
        } finally {
            document.close()
        }
    }

    private inline fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T? {
        val descriptor = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return null
        return try {
            PdfRenderer(descriptor).use { block(it) }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { descriptor.close() }
        }
    }
}
