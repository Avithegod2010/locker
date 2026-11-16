package app.locker.vault

import app.locker.vault.data.TrialContent
import app.locker.vault.data.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialContentTest {
    @Test
    fun everySampleIsUsableAndClearlyMarked() {
        assertTrue(TrialContent.samples.size >= 4)
        TrialContent.samples.forEach { sample ->
            assertTrue("title must not be blank", sample.title.isNotBlank())
            assertTrue("samples must be backdated or fresh", sample.daysAgo >= 0)
        }
        assertTrue(TrialContent.TAG.isNotBlank())
    }

    @Test
    fun samplesCoverTheMainVaultTypes() {
        val types = TrialContent.samples.map { it.type }.toSet()
        assertTrue(VaultType.NOTE in types)
        assertTrue(VaultType.SECRET in types)
        assertTrue(VaultType.THING in types)
    }

    @Test
    fun samplesDoNotPretendToBeRealCredentials() {
        // A trial vault uses a public password, so the content must never look like a real
        // account someone might copy into a real service.
        val suspicious = listOf(
            "@gmail.com",
            "@outlook.com",
            "@icloud.com",
            "password123",
            "changeme"
        )
        TrialContent.samples.forEach { sample ->
            val haystack = (sample.username + " " + sample.password + " " + sample.body)
                .lowercase()
            suspicious.forEach { fragment ->
                assertTrue(
                    "sample '${sample.title}' contains '$fragment'",
                    !haystack.contains(fragment)
                )
            }
        }
    }

    @Test
    fun welcomeSampleIsPinnedToTheCurrentDay() {
        val welcome = TrialContent.samples.first()
        assertEquals(0, welcome.daysAgo)
        assertTrue(welcome.body.contains("password", ignoreCase = true))
    }
}
