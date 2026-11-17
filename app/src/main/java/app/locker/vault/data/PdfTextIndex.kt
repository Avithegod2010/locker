package app.locker.vault.data

/** Text recognised from one PDF page, 1-based page number. */
data class PageText(val page: Int, val text: String)

/** Result of indexing a PDF: the searchable text plus how many pages produced any. */
data class PdfIndex(val text: String, val pagesIndexed: Int)

/**
 * Pure helpers for the PDF text index. OCR is expensive, so the caps and joins are decided
 * here and covered by unit tests rather than buried in the renderer loop.
 *
 * The index is not a separate database: it is stored in the encrypted `ocrText` field of the
 * PDF item, which global search already reads.
 */
object PdfTextIndex {
    const val MAX_CHARS = 20_000
    const val DEFAULT_MAX_PAGES = 12

    /** How many pages to render, clamped to the document and to a sane upper bound. */
    fun pageLimit(pageCount: Int, maxPages: Int = DEFAULT_MAX_PAGES): Int {
        if (pageCount <= 0) return 0
        return minOf(pageCount, maxPages.coerceAtLeast(1))
    }

    /**
     * Joins per-page text, skipping pages with no recognised text and stopping before the
     * character cap so a huge document cannot bloat the encrypted payload.
     */
    fun merge(pages: List<PageText>, maxChars: Int = MAX_CHARS): PdfIndex {
        val limit = maxChars.coerceAtLeast(1)
        val builder = StringBuilder()
        var indexed = 0
        for (page in pages.sortedBy { it.page }) {
            val text = page.text.trim()
            if (text.isEmpty()) continue
            val separator = if (builder.isEmpty()) "" else "\n\n"
            if (builder.length + separator.length + text.length > limit) {
                val room = limit - builder.length - separator.length
                if (room > 0) {
                    builder.append(separator).append(text.take(room))
                    indexed++
                }
                break
            }
            builder.append(separator).append(text)
            indexed++
        }
        return PdfIndex(text = builder.toString(), pagesIndexed = indexed)
    }

    fun isSearchable(ocrText: String): Boolean = ocrText.isNotBlank()

    /** A short one-paragraph preview for the viewer, collapsed to a single line of prose. */
    fun preview(ocrText: String, maxChars: Int = 180): String {
        val collapsed = ocrText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
        if (collapsed.length <= maxChars) return collapsed
        return collapsed.take(maxChars.coerceAtLeast(1)).trimEnd() + "…"
    }
}
