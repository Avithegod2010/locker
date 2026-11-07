package app.locker.vault

import app.locker.vault.core.Base32
import app.locker.vault.core.RecoveryShares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecoverySharesTest {
    private val digits = "123456789012345678901234"
    private val code = digits + Base32.checkDigit(digits)

    @Test
    fun anyTwoSharesReconstructTheRecoveryCode() {
        val shares = RecoveryShares.split(code)
        assertEquals(3, shares.size)
        listOf(0 to 1, 0 to 2, 1 to 2).forEach { (a, b) ->
            assertEquals(
                Base32.formatForDisplay(code),
                RecoveryShares.combine(listOf(shares[a], shares[b]))
            )
        }
    }

    @Test
    fun oneShareIsNotEnough() {
        val shares = RecoveryShares.split(code)
        assertNull(RecoveryShares.combine(listOf(shares.first())))
    }

    @Test
    fun aTamperedShareIsRejected() {
        val shares = RecoveryShares.split(code)
        val tampered = shares[0].dropLast(2) + "00"
        assertNull(RecoveryShares.combine(listOf(tampered, shares[1])))
    }

    @Test
    fun encodedShareRoundTrips() {
        val encoded = RecoveryShares.split(code)[1]
        val decoded = RecoveryShares.decode(encoded)
        assertNotNull(decoded)
        assertEquals(2, decoded?.index)
        assertEquals(25, decoded?.bytes?.size)
    }
}
