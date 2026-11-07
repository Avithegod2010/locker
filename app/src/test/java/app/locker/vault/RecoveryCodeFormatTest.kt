package app.locker.vault

import app.locker.vault.core.Base32
import app.locker.vault.core.Crypto
import app.locker.vault.core.RecoveryCodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeFormatTest {

    @Test
    fun `numeric key is displayed as five groups of five`() {
        val (_, display) = Base32.createRecoverySecret()
        val formatted = RecoveryCodeFormat.display(display)
        assertEquals(5, formatted.split(" ").size)
        assertTrue(formatted.split(" ").all { it.length == 5 })
        assertTrue(RecoveryCodeFormat.isNumeric(display))
    }

    @Test
    fun `verification uses groups two and four`() {
        val (_, display) = Base32.createRecoverySecret()
        val groups = RecoveryCodeFormat.normalize(display).chunked(5)
        val expected = RecoveryCodeFormat.verificationGroups(display)
        assertEquals(listOf(groups[1], groups[3]), expected)
        assertTrue(RecoveryCodeFormat.verify(display, expected))
    }

    @Test
    fun `answers tolerate spaces and lower case`() {
        val (_, display) = Base32.createRecoverySecret()
        val expected = RecoveryCodeFormat.verificationGroups(display)
        val sloppy = expected.map { " ${it.lowercase()} " }
        assertTrue(RecoveryCodeFormat.verify(display, sloppy))
    }

    @Test
    fun `wrong or missing groups fail verification`() {
        val (_, display) = Base32.createRecoverySecret()
        val expected = RecoveryCodeFormat.verificationGroups(display)
        val replacement = '0' + ((expected[0].last() - '0' + 1) % 10)
        val wrong = listOf(expected[0].dropLast(1) + replacement, expected[1])
        assertFalse(RecoveryCodeFormat.verify(display, wrong))
        assertFalse(RecoveryCodeFormat.verify(display, listOf(expected[0], "")))
        assertFalse(RecoveryCodeFormat.verify(display, emptyList()))
    }

    @Test
    fun `legacy keys keep their grouping and are not used for numeric verification`() {
        val secret = Crypto.randomBytes(32)
        val legacy = Base32.encode(secret + Crypto.sha256(secret).first())
        val display = RecoveryCodeFormat.display(legacy)
        assertFalse(RecoveryCodeFormat.isNumeric(display))
        assertTrue(RecoveryCodeFormat.verificationGroups(display).isEmpty())
        assertFalse(RecoveryCodeFormat.verify(display, listOf("12345", "67890")))
        assertTrue(display.lines().all { line -> line.split(" ").all { it.length <= 4 } })
    }

    @Test
    fun `whitespace is stripped when normalizing`() {
        assertEquals("12345ABC", RecoveryCodeFormat.normalize(" 123 45\n abc "))
    }
}
