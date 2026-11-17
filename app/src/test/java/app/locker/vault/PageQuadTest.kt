package app.locker.vault

import app.locker.vault.data.PageQuad
import app.locker.vault.data.PageQuadDetector
import app.locker.vault.data.QuadPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PageQuadTest {
    private val white = 0xFFFFFFFF.toInt()
    private val grey = 0xFF808080.toInt()

    private fun frame(
        width: Int = 200,
        height: Int = 260,
        corners: List<Pair<Int, Int>>
    ): IntArray {
        val pixels = IntArray(width * height) { white }
        if (corners.size == 4) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (inside(x, y, corners)) pixels[y * width + x] = grey
                }
            }
        }
        return pixels
    }

    /** Ray casting, enough for a convex test shape. */
    private fun inside(x: Int, y: Int, polygon: List<Pair<Int, Int>>): Boolean {
        var result = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi).toFloat() + xi) {
                result = !result
            }
            j = i
        }
        return result
    }

    @Test
    fun detectsATiltedPage() {
        val corners = listOf(40 to 20, 170 to 35, 160 to 230, 30 to 215)
        val quad = PageQuadDetector.detect(frame(corners = corners), 200, 260)
        assertNotNull(quad)
        quad!!
        assertTrue(abs(quad.topLeft.x - 40f) < 12f)
        assertTrue(abs(quad.topLeft.y - 20f) < 12f)
        assertTrue(abs(quad.bottomRight.x - 160f) < 12f)
        assertTrue(abs(quad.bottomRight.y - 230f) < 12f)
        assertTrue(quad.looksLikeAPage)
    }

    @Test
    fun refusesAnEmptyFrame() {
        assertNull(PageQuadDetector.detect(frame(corners = emptyList()), 200, 260))
    }

    @Test
    fun refusesAFrameThatIsAlmostEntirelyContent() {
        val corners = listOf(0 to 0, 199 to 0, 199 to 259, 0 to 259)
        assertNull(PageQuadDetector.detect(frame(corners = corners), 200, 260))
    }

    @Test
    fun refusesATinySmudge() {
        val corners = listOf(90 to 120, 110 to 120, 110 to 140, 90 to 140)
        assertNull(PageQuadDetector.detect(frame(corners = corners), 200, 260))
    }

    @Test
    fun tinyFramesAreIgnored() {
        assertNull(PageQuadDetector.detect(IntArray(10 * 10), 10, 10))
    }

    @Test
    fun areaAndShapeChecksWork() {
        val quad = PageQuad(
            topLeft = QuadPoint(10f, 10f),
            topRight = QuadPoint(110f, 10f),
            bottomRight = QuadPoint(110f, 210f),
            bottomLeft = QuadPoint(10f, 210f)
        )
        assertEquals(20_000f, quad.area, 1f)
        assertTrue(quad.looksLikeAPage)
        assertEquals(200f, quad.longestEdge, 0.001f)
    }

    @Test
    fun aWildQuadIsRejected() {
        val skewed = PageQuad(
            topLeft = QuadPoint(0f, 0f),
            topRight = QuadPoint(400f, 0f),
            bottomRight = QuadPoint(400f, 2f),
            bottomLeft = QuadPoint(0f, 200f)
        )
        assertTrue(!skewed.looksLikeAPage)
    }
}
