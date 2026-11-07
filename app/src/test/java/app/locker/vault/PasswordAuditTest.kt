package app.locker.vault

import app.locker.vault.data.ExpiryBucket
import app.locker.vault.data.PasswordAudit
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PasswordAuditTest {

    private val now = 1_700_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun item(
        id: String,
        expiresAt: Long,
        deletedAt: Long? = null
    ) = VaultItem(
        id = id,
        type = VaultType.THING,
        title = id,
        expiresAt = expiresAt,
        deletedAt = deletedAt
    )

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

    @Test
    fun `expired items are listed oldest first and skip deleted entries`() {
        val items = listOf(
            item("newer", now - day),
            item("older", now - 5 * day),
            item("deleted", now - day, deletedAt = now),
            item("none", 0L),
            item("future", now + day)
        )
        assertEquals(listOf("older", "newer"), PasswordAudit.expired(items, now).map { it.id })
        assertEquals(2, PasswordAudit.expiredCount(items, now))
    }

    @Test
    fun `expiring soon includes the thirty day boundary but not later`() {
        val items = listOf(
            item("today", now),
            item("tomorrow", now + day),
            item("boundary", now + 30 * day),
            item("later", now + 30 * day + 1),
            item("past", now - 1)
        )
        assertEquals(
            listOf("tomorrow", "boundary"),
            PasswordAudit.expiringSoon(items, now).map { it.id }
        )
        assertEquals(2, PasswordAudit.expiringSoonCount(items, now))
    }

    @Test
    fun `expiry buckets classify dates for badges`() {
        assertEquals(ExpiryBucket.EXPIRED, PasswordAudit.expiryBucket(now, now))
        assertEquals(ExpiryBucket.SOON, PasswordAudit.expiryBucket(now + day, now))
        assertEquals(ExpiryBucket.LATER, PasswordAudit.expiryBucket(now + 60 * day, now))
        assertNull(PasswordAudit.expiryBucket(0L, now))
    }
}
