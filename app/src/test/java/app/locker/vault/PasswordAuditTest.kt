package app.locker.vault

import app.locker.vault.data.PasswordAudit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordAuditTest {

    @Test
    fun `common passwords with suffixes are flagged`() {
        assertTrue(PasswordAudit.isPredictable("Sunshine2024!"))
        assertTrue(PasswordAudit.isPredictable("P@ssw0rd"))
        assertTrue(PasswordAudit.isPredictable("qwerty123"))
        assertTrue(PasswordAudit.isPredictable("Welcome1"))
    }

    @Test
    fun `sequences, repeats, and years are flagged`() {
        assertTrue(PasswordAudit.isPredictable("123456"))
        assertTrue(PasswordAudit.isPredictable("abcdef"))
        assertTrue(PasswordAudit.isPredictable("aaaaaa"))
        assertTrue(PasswordAudit.isPredictable("abcabcabc"))
        assertTrue(PasswordAudit.isPredictable("2026"))
        assertTrue(PasswordAudit.isPredictable("987654"))
    }

    @Test
    fun `strong random passwords pass`() {
        assertFalse(PasswordAudit.isPredictable("j8K#pQ2!vL9"))
        assertFalse(PasswordAudit.isPredictable("Tr0ub4dor&3"))
        assertFalse(PasswordAudit.isPredictable("m7\$Qw2!zR9x"))
    }
}
