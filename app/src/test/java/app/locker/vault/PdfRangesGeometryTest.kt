package app.locker.vault

import app.locker.vault.data.PdfGeometry
import app.locker.vault.data.PdfRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRangesTest {
    @Test
    fun singlePagesAndRangesParse() {
        assertEquals(listOf(1), PdfRanges.parse("1", 10))
        assertEquals(listOf(1, 2, 3), PdfRanges.parse("1-3", 10))
        assertEquals(listOf(2, 3, 7), PdfRanges.parse("2-3, 7", 10))
        assertEquals(listOf(5, 6, 7, 8, 9, 10), PdfRanges.parse("5-", 10))
        assertEquals(listOf(1, 2, 3), PdfRanges.parse("-3", 10))
    }

    @Test
    fun duplicatesAndOrderAreNormalised() {
        assertEquals(listOf(1, 2, 3), PdfRanges.parse("3,1,2,1", 10))
        assertEquals(listOf(1, 2, 3, 4), PdfRanges.parse("1-3, 2-4", 10))
        assertEquals(listOf(1, 4), PdfRanges.parse("1;4", 10))
    }

    @Test
    fun outOfRangeAndJunkAreIgnored() {
        assertEquals(listOf(10), PdfRanges.parse("10, 11, 99", 10))
        assertEquals(emptyList<Int>(), PdfRanges.parse("11-14", 10))
        assertEquals(emptyList<Int>(), PdfRanges.parse("5-2", 10))
        assertEquals(emptyList<Int>(), PdfRanges.parse("abc", 10))
        assertEquals(emptyList<Int>(), PdfRanges.parse("", 10))
        assertEquals(listOf(2), PdfRanges.parse("abc, 2, x-y", 10))
        assertEquals(emptyList<Int>(), PdfRanges.parse("1", 0))
    }

    @Test
    fun hintDescribesTheDocument() {
        assertTrue(PdfRanges.hint(9).contains("1-9"))
        assertEquals("This document has 1 page.", PdfRanges.hint(1))
    }
}

class PdfGeometryTest {
    @Test
    fun aDragInEitherDirectionProducesTheSameRect() {
        val forward = PdfGeometry.normalize(10f, 20f, 110f, 220f, 200f, 400f)
        val backward = PdfGeometry.normalize(110f, 220f, 10f, 20f, 200f, 400f)
        assertEquals(forward, backward)
        assertEquals(0.05f, forward.left, 0.0001f)
        assertEquals(0.55f, forward.right, 0.0001f)
    }

    @Test
    fun dragsOutsideThePageAreClamped() {
        val rect = PdfGeometry.normalize(-50f, -50f, 500f, 600f, 200f, 400f)
        assertEquals(0f, rect.left, 0f)
        assertEquals(0f, rect.top, 0f)
        assertEquals(1f, rect.right, 0f)
        assertEquals(1f, rect.bottom, 0f)
    }

    @Test
    fun aTapIsNotWorthRedacting() {
        val tap = PdfGeometry.normalize(50f, 50f, 51f, 51f, 200f, 400f)
        assertFalse(PdfGeometry.isMeaningful(tap))
        val drag = PdfGeometry.normalize(20f, 20f, 120f, 90f, 200f, 400f)
        assertTrue(PdfGeometry.isMeaningful(drag))
    }

    @Test
    fun aDegenerateViewCannotProduceNegativeBounds() {
        assertTrue(PdfGeometry.normalize(0f, 0f, 10f, 10f, 0f, 0f).isEmpty)
    }

    @Test
    fun pixelsMapInsideThePage() {
        val rect = PdfGeometry.normalize(10f, 20f, 110f, 220f, 200f, 400f)
        val pixels = PdfGeometry.toPixels(rect, 1000, 2000)
        assertEquals(listOf(50, 100, 550, 1100), pixels.toList())
    }

    @Test
    fun paddingGrowsTheBoxButStaysOnThePage() {
        val edge = PdfGeometry.normalize(0f, 0f, 200f, 400f, 200f, 400f)
        val padded = PdfGeometry.pad(edge, 0.02f)
        assertEquals(0f, padded.left, 0f)
        assertEquals(1f, padded.bottom, 0f)

        val inner = PdfGeometry.normalize(80f, 160f, 120f, 240f, 200f, 400f)
        val grown = PdfGeometry.pad(inner, 0.01f)
        assertEquals(0.39f, grown.left, 0.0001f)
        assertEquals(0.61f, grown.bottom, 0.0001f)
    }
}
