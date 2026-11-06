package app.locker.vault

import app.locker.vault.core.Base32
import app.locker.vault.core.Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {

    @Test
    fun `numeric recovery code has 24 secret digits and a valid check digit`() {
        val (secret, display) = Base32.createRecoverySecret()
        val digits = display.filter { it.isDigit() }
        assertEquals(25, digits.length)
        assertEquals(24, secret.size)
        assertEquals(String(secret, Charsets.UTF_8), digits.dropLast(1))
        assertEquals(Base32.checkDigit(digits.dropLast(1)), digits.last())
        assertTrue(display.matches(Regex("^\\d{5}( \\d{5}){4}$")))
    }

    @Test
    fun `numeric recovery code round-trips through the parser`() {
        repeat(10) {
            val (secret, display) = Base32.createRecoverySecret()
            val parsed = Base32.parseRecoverySecret(display)
            assertArrayEquals(secret, parsed)
        }
    }

    @Test
    fun `passed in without separators still parses`() {
        val (secret, display) = Base32.createRecoverySecret()
        assertArrayEquals(secret, Base32.parseRecoverySecret(display.replace(" ", "")))
    }

    @Test
    fun `look-alike characters typed by hand are normalised`() {
        val (secret, display) = Base32.createRecoverySecret()
        val mangled = display
            .replace('0', 'O')
            .replace('1', 'l')
        assertArrayEquals(secret, Base32.parseRecoverySecret(mangled))
    }

    @Test
    fun `a single wrong digit is rejected by the check digit`() {
        val (_, display) = Base32.createRecoverySecret()
        val digits = display.filter { it.isDigit() }
        val last = digits.last()
        val replacement = if (last == '9') '0' else (last + 1)
        val tampered = digits.dropLast(1) + replacement
        assertNull(Base32.parseRecoverySecret(tampered))
    }

    @Test
    fun `legacy base32 recovery keys still unlock`() {
        val secret = Crypto.randomBytes(32)
        val legacy = secret + Crypto.sha256(secret).first()
        val display = Base32.formatForDisplay(Base32.encode(legacy))
        val parsed = Base32.parseRecoverySecret(display)
        assertArrayEquals(secret, parsed)
    }

    @Test
    fun `garbage input is rejected`() {
        assertNull(Base32.parseRecoverySecret(""))
        assertNull(Base32.parseRecoverySecret("hello"))
        assertNull(Base32.parseRecoverySecret("123"))
        assertNull(Base32.parseRecoverySecret("11111 11111 11111 11111 1111"))
    }
}
