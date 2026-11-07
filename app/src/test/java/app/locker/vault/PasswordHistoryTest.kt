package app.locker.vault

import app.locker.vault.data.PasswordHistory
import app.locker.vault.data.PasswordHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHistoryTest {

    @Test
    fun `newest entry is first`() {
        val history = PasswordHistory.append(
            listOf(PasswordHistoryEntry("older", 1L)),
            previousPassword = "previous",
            changedAt = 10L
        )
        assertEquals(listOf("previous", "older"), history.map { it.password })
        assertEquals(10L, history.first().changedAt)
    }

    @Test
    fun `blank and consecutive duplicates are ignored`() {
        val existing = listOf(PasswordHistoryEntry("same", 1L))
        assertEquals(existing, PasswordHistory.append(existing, "", 5L))
        assertEquals(existing, PasswordHistory.append(existing, "same", 5L))
    }

    @Test
    fun `duplicates further back are collapsed`() {
        val history = PasswordHistory.append(
            listOf(
                PasswordHistoryEntry("current-history", 2L),
                PasswordHistoryEntry("reused", 1L)
            ),
            previousPassword = "reused",
            changedAt = 3L
        )
        assertEquals(listOf("reused", "current-history"), history.map { it.password })
    }

    @Test
    fun `only the newest ten entries are retained`() {
        var history = emptyList<PasswordHistoryEntry>()
        repeat(15) { index ->
            history = PasswordHistory.append(history, "password-$index", index.toLong())
        }
        assertEquals(PasswordHistory.MAX_ENTRIES, history.size)
        assertEquals("password-14", history.first().password)
        assertTrue(history.none { it.password == "password-4" })
    }
}
