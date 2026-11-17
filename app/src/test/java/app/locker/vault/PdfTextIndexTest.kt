package app.locker.vault

import app.locker.vault.data.PageText
import app.locker.vault.data.PdfTextIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextIndexTest {
    @Test
    fun pageLimitClampsToDocumentAndCap() {
        assertEquals(0, PdfTextIndex.pageLimit(0))
        assertEquals(0, PdfTextIndex.pageLimit(-4))
        assertEquals(5, PdfTextIndex.pageLimit(5))
        assertEquals(12, PdfTextIndex.pageLimit(200))
        assertEquals(3, PdfTextIndex.pageLimit(200, maxPages = 3))
        assertEquals(1, PdfTextIndex.pageLimit(10, maxPages = 0))
    }

    @Test
    fun mergeSkipsBlankPagesAndKeepsOrder() {
        val index = PdfTextIndex.merge(
            listOf(
                PageText(3, "third"),
                PageText(1, " first "),
                PageText(2, "   \n  "),
                PageText(4, "fourth")
            )
        )
        assertEquals("first\n\nthird\n\nfourth", index.text)
        assertEquals(3, index.pagesIndexed)
    }

    @Test
    fun mergeStopsAtTheCharacterCap() {
        val index = PdfTextIndex.merge(
            listOf(PageText(1, "a".repeat(10)), PageText(2, "b".repeat(10))),
            maxChars = 15
        )
        assertEquals(15, index.text.length)
        assertTrue(index.text.startsWith("a".repeat(10)))
        assertEquals(2, index.pagesIndexed)
    }

    @Test
    fun mergeWithNoTextIsEmpty() {
        val index = PdfTextIndex.merge(emptyList())
        assertEquals("", index.text)
        assertEquals(0, index.pagesIndexed)
        assertFalse(PdfTextIndex.isSearchable(index.text))
    }

    @Test
    fun aSingleOverlongPageIsTruncatedButCounted() {
        val index = PdfTextIndex.merge(listOf(PageText(1, "x".repeat(100))), maxChars = 40)
        assertEquals(40, index.text.length)
        assertEquals(1, index.pagesIndexed)
    }

    @Test
    fun previewCollapsesWhitespaceAndEllipsises() {
        assertEquals("one two three", PdfTextIndex.preview("one\n  two\tthree"))
        val long = "word ".repeat(60)
        val preview = PdfTextIndex.preview(long, maxChars = 20)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 21)
    }

    @Test
    fun whitespaceOnlyIndexIsNotSearchable() {
        assertFalse(PdfTextIndex.isSearchable("  \n\t "))
        assertTrue(PdfTextIndex.isSearchable("Invoice 42"))
    }
}
