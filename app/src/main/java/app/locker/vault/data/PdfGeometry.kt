package app.locker.vault.data

/** A rectangle in page-relative coordinates: 0..1 from the top-left of the rendered page. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * Geometry for drawing redaction boxes on a page. Kept free of Android and Compose types so the
 * clamping rules (inverted drags, touches outside the page, tiny accidental taps) are unit tested.
 */
object PdfGeometry {
    /** The smallest selection worth burning into the document, as a fraction of the page. */
    const val MIN_SIZE = 0.01f

    /**
     * Normalises a drag inside a view of [viewWidth] x [viewHeight]. Coordinates outside the view
     * are clamped, and a drag in any direction produces a positive rectangle.
     */
    fun normalize(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        viewWidth: Float,
        viewHeight: Float
    ): NormalizedRect {
        if (viewWidth <= 0f || viewHeight <= 0f) {
            return NormalizedRect(0f, 0f, 0f, 0f)
        }
        val x1 = (startX / viewWidth).coerceIn(0f, 1f)
        val x2 = (endX / viewWidth).coerceIn(0f, 1f)
        val y1 = (startY / viewHeight).coerceIn(0f, 1f)
        val y2 = (endY / viewHeight).coerceIn(0f, 1f)
        return NormalizedRect(
            left = minOf(x1, x2),
            top = minOf(y1, y2),
            right = maxOf(x1, x2),
            bottom = maxOf(y1, y2)
        )
    }

    fun isMeaningful(rect: NormalizedRect): Boolean =
        rect.width >= MIN_SIZE && rect.height >= MIN_SIZE

    /** Converts to integer pixel bounds for a rendered page bitmap. */
    fun toPixels(rect: NormalizedRect, pageWidth: Int, pageHeight: Int): IntArray {
        val left = (rect.left * pageWidth).toInt().coerceIn(0, pageWidth)
        val top = (rect.top * pageHeight).toInt().coerceIn(0, pageHeight)
        val right = (rect.right * pageWidth).toInt().coerceIn(left, pageWidth)
        val bottom = (rect.bottom * pageHeight).toInt().coerceIn(top, pageHeight)
        return intArrayOf(left, top, right, bottom)
    }

    /**
     * Slightly enlarges a box before burning it in. A redaction that exactly hugs the glyphs can
     * leave a readable sliver after scaling, so the margin is deliberate.
     */
    fun pad(rect: NormalizedRect, margin: Float = 0.004f): NormalizedRect {
        val m = margin.coerceAtLeast(0f)
        return NormalizedRect(
            left = (rect.left - m).coerceIn(0f, 1f),
            top = (rect.top - m).coerceIn(0f, 1f),
            right = (rect.right + m).coerceIn(0f, 1f),
            bottom = (rect.bottom + m).coerceIn(0f, 1f)
        )
    }
}
