package app.locker.vault

import app.locker.vault.core.TrialVault
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialVaultTest {
    @Test
    fun defaultPasswordMeetsTheVaultMinimum() {
        assertTrue(TrialVault.DEFAULT_PASSWORD.length >= 10)
    }

    @Test
    fun defaultPasswordIsRecognised() {
        assertTrue(TrialVault.isDefaultPassword("Qwerty1234567"))
        assertFalse(TrialVault.isDefaultPassword("Qwerty1234567!"))
    }
}
