package app.locker.vault.core

/**
 * Shared formatting and verification for recovery keys.
 *
 * Numeric keys are 25 digits (24 secret digits + one check digit) displayed as five groups of
 * five. During onboarding the user re-types groups 2 and 4, which proves they wrote the key
 * down without asking them to copy the whole thing. Legacy Base32 keys remain readable and are
 * grouped in fours.
 */
object RecoveryCodeFormat {
    const val NUMERIC_DIGITS = 25
    private const val GROUP_SIZE = 5

    fun normalize(input: String): String =
        input.filterNot { it.isWhitespace() }.uppercase()

    fun isNumeric(code: String): Boolean {
        val cleaned = normalize(code)
        return cleaned.length == NUMERIC_DIGITS && cleaned.all { it.isDigit() }
    }

    /** Single-line numeric display, or four-character rows for a legacy Base32 key. */
    fun display(code: String): String {
        val cleaned = normalize(code)
        if (cleaned.isBlank()) return ""
        return if (isNumeric(cleaned)) {
            cleaned.chunked(GROUP_SIZE).joinToString(" ")
        } else {
            cleaned.chunked(4).chunked(GROUP_SIZE).joinToString("\n") { it.joinToString(" ") }
        }
    }

    /** The groups the user is asked to confirm: the second and fourth five-digit groups. */
    fun verificationGroups(code: String): List<String> {
        if (!isNumeric(code)) return emptyList()
        val groups = normalize(code).chunked(GROUP_SIZE)
        return listOfNotNull(groups.getOrNull(1), groups.getOrNull(3))
    }

    /**
     * Compares two typed answers with the verification groups. Whitespace and case are
     * ignored, so answers copied by hand or with stray spaces still match.
     */
    fun verify(code: String, answers: List<String>): Boolean {
        val expected = verificationGroups(code)
        if (expected.isEmpty()) return false
        return expected.indices.all { index ->
            val answer = normalize(answers.getOrNull(index).orEmpty())
            answer.isNotEmpty() && answer == expected[index]
        }
    }
}
