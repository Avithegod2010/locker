package app.locker.vault.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point in bitmap coordinates. */
data class QuadPoint(val x: Float, val y: Float)

/**
 * The four corners of a photographed page, in reading order. Kept as plain floats so the
 * detector and the warp maths can be unit tested without Android.
 */
data class PageQuad(
    val topLeft: QuadPoint,
    val topRight: QuadPoint,
    val bottomRight: QuadPoint,
    val bottomLeft: QuadPoint
) {
    val isFinite: Boolean
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft).all {
            it.x.isFinite() && it.y.isFinite()
        }

    /** Shoelace area, always positive for a proper quadrilateral. */
    val area: Float
        get() {
            val pts = listOf(topLeft, topRight, bottomRight, bottomLeft)
            var sum = 0f
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2f
        }

    /** Longest edge, used to size the rectified output. */
    val longestEdge: Float
        get() = listOf(
            hypot(topRight.x - topLeft.x, topRight.y - topLeft.y),
            hypot(bottomRight.x - bottomLeft.x, bottomRight.y - bottomLeft.y),
            hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y),
            hypot(bottomRight.x - topRight.x, bottomRight.y - topRight.y)
        ).maxOrNull() ?: 0f

    /** True when opposite edges are at least roughly parallel; guards against wild detections. */
    val looksLikeAPage: Boolean
        get() {
            if (!isFinite) return false
            val top = hypot(topRight.x - topLeft.x, topRight.y - topLeft.y)
            val bottom = hypot(bottomRight.x - bottomLeft.x, bottomRight.y - bottomLeft.y)
            val left = hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y)
            val right = hypot(bottomRight.x - topRight.x, bottomRight.y - topRight.y)
            if (min(top, bottom) < 1f || min(left, right) < 1f) return false
            val horizontal = max(top, bottom) / min(top, bottom)
            val vertical = max(left, right) / min(left, right)
            return horizontal <= 2.2f && vertical <= 2.2f
        }
}

/**
 * Estimates the page corners from a photographed frame.
 *
 * The background colour comes from the frame's corners, exactly like the border trimmer. Every
 * pixel that differs from it is "content", and the four extreme content points along the
 * diagonals become the corners. This is deliberately forgiving: when the result is not
 * convincingly page-shaped the caller keeps the un-warped image rather than mangling it.
 */
object PageQuadDetector {
    private const val CONTENT_DISTANCE = 90

    fun detect(pixels: IntArray, width: Int, height: Int): PageQuad? {
        if (width < 40 || height < 40 || pixels.size < width * height) return null

        val bg = backgroundColour(pixels, width, height)
        var topLeft: QuadPoint? = null
        var topRight: QuadPoint? = null
        var bottomRight: QuadPoint? = null
        var bottomLeft: QuadPoint? = null
        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE
        var content = 0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val color = pixels[row + x]
                val distance = abs((color shr 16 and 0xFF) - bg[0]) +
                    abs((color shr 8 and 0xFF) - bg[1]) +
                    abs((color and 0xFF) - bg[2])
                if (distance <= CONTENT_DISTANCE) continue
                content++
                val sum = (x + y).toFloat()
                val diff = (x - y).toFloat()
                if (sum < minSum) { minSum = sum; topLeft = QuadPoint(x.toFloat(), y.toFloat()) }
                if (sum > maxSum) { maxSum = sum; bottomRight = QuadPoint(x.toFloat(), y.toFloat()) }
                if (diff > maxDiff) { maxDiff = diff; topRight = QuadPoint(x.toFloat(), y.toFloat()) }
                if (diff < minDiff) { minDiff = diff; bottomLeft = QuadPoint(x.toFloat(), y.toFloat()) }
            }
        }

        // A page should cover a meaningful part of the frame without filling it: a wall or a
        // desk edge often qualifies as "content", so a very large or very small blob is refused.
        val coverage = content.toFloat() / (width * height)
        if (coverage < 0.25f || coverage > 0.85f) return null

        val quad = PageQuad(
            topLeft = topLeft ?: return null,
            topRight = topRight ?: return null,
            bottomRight = bottomRight ?: return null,
            bottomLeft = bottomLeft ?: return null
        )
        if (!quad.looksLikeAPage) return null
        if (quad.area < width * height * 0.20f) return null
        // A real page photographed whole has all four corners inside the frame. Anything touching
        // the border is either cropped or a room corner, and warping it would ruin the capture.
        val marginX = width * 0.02f
        val marginY = height * 0.02f
        val inside = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
            .all { it.x > marginX && it.x < width - marginX && it.y > marginY && it.y < height - marginY }
        if (!inside) return null
        // Portrait and landscape pages sit between roughly 0.5 and 2.0; a sliver is not a page.
        val widthEdge = (
            kotlin.math.hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y) +
                kotlin.math.hypot(
                    quad.bottomRight.x - quad.bottomLeft.x,
                    quad.bottomRight.y - quad.bottomLeft.y
                )
            ) / 2f
        val heightEdge = (
            kotlin.math.hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y) +
                kotlin.math.hypot(
                    quad.bottomRight.x - quad.topRight.x,
                    quad.bottomRight.y - quad.topRight.y
                )
            ) / 2f
        if (widthEdge <= 0f || heightEdge <= 0f) return null
        val aspect = widthEdge / heightEdge
        if (aspect < 0.45f || aspect > 2.2f) return null
        return quad
    }

    private fun backgroundColour(pixels: IntArray, width: Int, height: Int): IntArray {
        val corners = intArrayOf(
            pixels[0],
            pixels[width - 1],
            pixels[(height - 1) * width],
            pixels[width * height - 1]
        )
        return intArrayOf(
            corners.sumOf { it shr 16 and 0xFF } / 4,
            corners.sumOf { it shr 8 and 0xFF } / 4,
            corners.sumOf { it and 0xFF } / 4
        )
    }
}
