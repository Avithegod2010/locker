package app.locker.vault

import app.locker.vault.core.TrialWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialWindowTest {
    private val start = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun endsAtAddsTheWindow() {
        assertEquals(start + 7 * day, TrialWindow.endsAt(start))
        assertEquals(start + 3 * day, TrialWindow.endsAt(start, days = 3))
    }

    @Test
    fun endsAtIsZeroWhenTheTrialNeverStarted() {
        assertEquals(0L, TrialWindow.endsAt(0L))
        assertEquals(0L, TrialWindow.daysLeft(0L, now = start))
        assertFalse(TrialWindow.isExpired(0L, now = start))
    }

    @Test
    fun daysLeftRoundsUpSoTheLastDayStillCounts() {
        assertEquals(7L, TrialWindow.daysLeft(start, now = start))
        assertEquals(1L, TrialWindow.daysLeft(start, now = start + 7 * day - 1))
        assertEquals(0L, TrialWindow.daysLeft(start, now = start + 7 * day))
        assertEquals(0L, TrialWindow.daysLeft(start, now = start + 30 * day))
    }

    @Test
    fun expiryIsInclusiveOfTheEndInstant() {
        assertFalse(TrialWindow.isExpired(start, now = start + 7 * day - 1))
        assertTrue(TrialWindow.isExpired(start, now = start + 7 * day))
    }

    @Test
    fun labelsReadNaturally() {
        assertEquals("Trial vault", TrialWindow.label(0L, now = start))
        assertEquals("7 days left", TrialWindow.label(start, now = start))
        assertEquals("1 day left", TrialWindow.label(start, now = start + 7 * day - 1))
        assertEquals("Trial expired", TrialWindow.label(start, now = start + 8 * day))
    }

    @Test
    fun aDegenerateWindowStillLandsInTheFuture() {
        // Guard against a zero or negative configured duration breaking the countdown.
        assertTrue(TrialWindow.endsAt(start, days = 0) > start)
        assertEquals(1L, TrialWindow.daysLeft(start, now = start, days = 0))
    }
}
