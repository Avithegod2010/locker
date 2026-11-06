package app.locker.vault

import app.locker.vault.core.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private fun options(
        length: Int = 22,
        digits: Boolean = true,
        symbols: Boolean = true,
        noAmbiguous: Boolean = true
    ) = PasswordGenerator.Options(
        length = length,
        digits = digits,
        symbols = symbols,
        noAmbiguous = noAmbiguous
    )

    @Test
    fun `respects length and includes every requested character class`() {
        repeat(20) {
            val value = PasswordGenerator.generate(options(length = 24))
            assertEquals(24, value.length)
            assertTrue(value.any { it.isDigit() })
            assertTrue(value.any { it.isLetter() })
            assertTrue(value.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `look-alike characters are excluded by default`() {
        repeat(20) {
            val value = PasswordGenerator.generate(options(length = 40))
            assertFalse(value.any { it in "lIO01" })
        }
    }

    @Test
    fun `digits and symbols can be turned off`() {
        repeat(10) {
            val value = PasswordGenerator.generate(
                options(length = 20, digits = false, symbols = false)
            )
            assertEquals(20, value.length)
            assertTrue(value.all { it.isLetter() })
        }
    }

    @Test
    fun `minimum supported length still includes all classes`() {
        val value = PasswordGenerator.generate(options(length = 10))
        assertEquals(10, value.length)
        assertTrue(value.any { it.isDigit() })
        assertTrue(value.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `two generated passwords are different`() {
        val a = PasswordGenerator.generate(options(length = 32))
        val b = PasswordGenerator.generate(options(length = 32))
        assertFalse(a == b)
    }
}
