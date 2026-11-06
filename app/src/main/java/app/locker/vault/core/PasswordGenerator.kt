package app.locker.vault.core

import java.security.SecureRandom

/**
 * Configurable offline password generator. Uses SecureRandom; the charset is built from the
 * user's toggles and ambiguity preference, and every requested character-class is guaranteed
 * to appear at least once when the length allows it.
 */
object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val LOWER_AMBIGUOUS = "l"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ" // no I, O by default
    private const val UPPER_AMBIGUOUS = "IO"
    private const val DIGITS = "23456789" // no 0, 1 by default
    private const val DIGITS_AMBIGUOUS = "01"
    private const val SYMBOLS = "!@#\$%^&*-_=+?"

    data class Options(
        val length: Int = 22,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val noAmbiguous: Boolean = true
    )

    fun generate(options: Options): String {
        val lower = if (options.noAmbiguous) LOWER else LOWER + LOWER_AMBIGUOUS
        val upper = if (options.noAmbiguous) UPPER else UPPER + UPPER_AMBIGUOUS
        val digits = if (options.noAmbiguous) DIGITS else DIGITS + DIGITS_AMBIGUOUS
        val groups = mutableListOf(lower, upper)
        if (options.digits) groups += digits
        if (options.symbols) groups += SYMBOLS
        require(groups.size <= options.length.coerceAtLeast(4)) {
            "Length must fit the chosen character classes"
        }
        val random = SecureRandom()
        val pool = groups.joinToString("")
        val chars = buildList {
            groups.forEach { group -> add(group[random.nextInt(group.length)]) }
            while (size < options.length) add(pool[random.nextInt(pool.length)])
        }.shuffled(random)
        return chars.joinToString("")
    }

    fun generate(settings: AppSettings): String = generate(
        Options(
            length = settings.generatorLength,
            digits = settings.generatorDigits,
            symbols = settings.generatorSymbols,
            noAmbiguous = settings.generatorNoAmbiguous
        )
    )
}
