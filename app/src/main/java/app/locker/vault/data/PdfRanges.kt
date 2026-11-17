package app.locker.vault.data

/**
 * Parses the page selection used by PDF split. Users type human ranges, so the parser is pure
 * and forgiving: "1-3, 5" and "7-" are accepted, out-of-range pages are dropped, duplicates are
 * removed, and the result is always in document order. A result with no pages is invalid.
 */
object PdfRanges {
    /** Returns 1-based page numbers, or an empty list when the expression selects nothing. */
    fun parse(spec: String, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        val selected = sortedSetOf<Int>()
        spec.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { part ->
                val range = part.split('-').map { it.trim() }
                when {
                    range.size == 1 -> add(selected, range[0], pageCount)
                    range.size == 2 -> {
                        val start = range[0]
                        val end = range[1]
                        if (start.isEmpty() && end.isEmpty()) return@forEach
                        if (start.isEmpty()) {
                            // "-5" means "up to page 5".
                            val last = end.toIntOrNull() ?: return@forEach
                            (1..last.coerceAtMost(pageCount)).forEach { selected.add(it) }
                        } else {
                            val first = start.toIntOrNull() ?: return@forEach
                            val last = if (end.isEmpty()) pageCount else end.toIntOrNull()
                                ?: return@forEach
                            if (first > last) return@forEach
                            (first..last.coerceAtLeast(first)).forEach {
                                if (it in 1..pageCount) selected.add(it)
                            }
                        }
                    }
                }
            }
        return selected.toList()
    }

    private fun add(target: MutableSet<Int>, value: String, pageCount: Int) {
        val page = value.toIntOrNull() ?: return
        if (page in 1..pageCount) target.add(page)
    }

    /** "1-3, 7" style hint shown under the field. */
    fun hint(pageCount: Int): String =
        if (pageCount <= 1) "This document has 1 page." else "1-$pageCount, for example “1-3, 7”."
}
