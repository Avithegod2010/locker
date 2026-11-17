package app.locker.vault

import app.locker.vault.data.MediaExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExportTest {
    @Test
    fun audioNamesAreSafeForTheVault() {
        assertEquals("Holiday_clip.m4a", MediaExport.audioFileName("Holiday clip.mp4"))
        assertEquals("audio.m4a", MediaExport.audioFileName("   "))
        assertEquals("already.m4a", MediaExport.audioFileName("already.m4a"))
    }

    @Test
    fun pathSeparatorsAndReservedCharactersAreRemoved() {
        val name = MediaExport.audioFileName("a/b\\c:d*e?f\"g<h>i|j.mp4")
        assertTrue(name.endsWith(".m4a"))
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').forEach {
            assertTrue("name still contains $it: $name", !name.contains(it))
        }
    }

    @Test
    fun longTitlesAreTruncated() {
        val name = MediaExport.audioFileName("x".repeat(200) + ".mp4")
        assertTrue(name.length <= 64)
        assertTrue(name.endsWith(".m4a"))
    }

    @Test
    fun emptyExtensionStillGetsOne() {
        assertEquals("memo.m4a", MediaExport.audioFileName("memo"))
    }
}
