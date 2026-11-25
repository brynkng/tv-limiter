package dev.tvlimiter.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThumbnailNamesTest {
    private val yt = "com.google.android.youtube.tv"

    @Test
    fun sameVideoGetsTheSameNameWhateverTheCaseOrSpacing() {
        assertEquals(ThumbnailNames.fileName(yt, "Big Buck Bunny ", "Blender"), ThumbnailNames.fileName(yt, "big buck bunny", " blender"))
    }

    @Test
    fun differentChannelOrAppGetsADifferentName() {
        val base = ThumbnailNames.fileName(yt, "Intro", "Channel A")
        assertNotEquals(base, ThumbnailNames.fileName(yt, "Intro", "Channel B"))
        assertNotEquals(base, ThumbnailNames.fileName("com.stremio.one", "Intro", "Channel A"))
    }

    @Test
    fun nameIsAShortJpegFileName() {
        val name = ThumbnailNames.fileName(yt, "Title", null)
        assertEquals(20, name.length)
        assert(name.endsWith(".jpg"))
    }
}
