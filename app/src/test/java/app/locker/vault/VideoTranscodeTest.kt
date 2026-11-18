package app.locker.vault

import app.locker.vault.data.VideoTranscode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTranscodeTest {
    private val hd = VideoTranscode.presetById("720p")!!
    private val sd = VideoTranscode.presetById("480p")!!

    @Test
    fun presetsExistAndAreDistinct() {
        assertEquals("720p", hd.id)
        assertEquals("480p", sd.id)
        assertNull(VideoTranscode.presetById("4k"))
        assertTrue(VideoTranscode.presets.size >= 2)
    }

    @Test
    fun landscapeScalesToThePreset() {
        assertEquals(1280 to 720, VideoTranscode.targetSize(1920, 1080, hd.maxDimension))
        assertEquals(854 to 480, VideoTranscode.targetSize(1920, 1080, sd.maxDimension))
    }

    @Test
    fun portraitKeepsItsOrientation() {
        val (width, height) = VideoTranscode.targetSize(1080, 1920, hd.maxDimension)
        assertEquals(720, width)
        assertEquals(1280, height)
    }

    @Test
    fun smallVideosAreNeverUpscaled() {
        assertEquals(640 to 360, VideoTranscode.targetSize(640, 360, hd.maxDimension))
        assertEquals(480 to 480, VideoTranscode.targetSize(480, 480, hd.maxDimension))
    }

    @Test
    fun oddSizesBecomeEven() {
        val (width, height) = VideoTranscode.targetSize(1281, 721, 1280)
        assertEquals(0, width % 2)
        assertEquals(0, height % 2)
        assertTrue(width <= 1280)
        assertTrue(height <= 720)
    }

    @Test
    fun degenerateSizesReturnZero() {
        assertEquals(0 to 0, VideoTranscode.targetSize(0, 720, 1280))
        assertEquals(0 to 0, VideoTranscode.targetSize(-4, 720, 1280))
    }

    @Test
    fun bitrateScalesWithPixelsAndIsClamped() {
        val small = VideoTranscode.bitrateFor(320, 240, 30)
        val large = VideoTranscode.bitrateFor(1920, 1080, 60)
        assertTrue(small >= 600_000)
        assertTrue(large <= 6_000_000)
        assertTrue(large > small)
    }

    @Test
    fun outputNamesAreSafe() {
        assertEquals("Holiday clip (720p).mp4", VideoTranscode.outputName("Holiday clip.mp4", "720p"))
        assertEquals("video (480p).mp4", VideoTranscode.outputName("   ", "480p"))
        val name = VideoTranscode.outputName("a/b:c*d?e\"f<g>h|i.mp4", "720p")
        assertTrue(name.endsWith("(720p).mp4"))
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').forEach {
            assertTrue("still contains $it: $name", !name.contains(it))
        }
    }
}
