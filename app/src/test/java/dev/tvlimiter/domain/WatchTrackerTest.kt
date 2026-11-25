package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTrackerTest {
    private val tracker = WatchTracker()
    private val reports = mutableListOf<Watch>()

    private fun video(title: String = "Video A", channel: String = "Channel A", seconds: Long = 600) = Playing(title, channel, seconds * 1000)

    /** Feeds the tracker the state at second [t]. The wall clock is offset so it can't be mistaken for the elapsed one. */
    private fun at(t: Int, playing: Playing?, suppressed: Boolean = false) {
        reports += tracker.update(Moment(t * 1000L, 1_000_000_000L + t * 1000L), playing, suppressed)
    }

    /** Plays [playing] from [from] to [to], updated every 3 seconds like the listener's check. */
    private fun play(from: Int, to: Int, playing: Playing?) {
        var t = from
        while (t < to) {
            at(t, playing)
            t += 3
        }
        at(to, playing)
    }

    @Test
    fun `a video played for under 5 seconds is not reported`() {
        play(0, 4, video())
        at(4, null)
        at(200, null)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `a video played for 5 seconds is reported once it goes quiet`() {
        play(0, 5, video(seconds = 213))
        at(6, null)
        assertTrue("held back while a replay could still join it", reports.isEmpty())
        at(70, null)
        assertEquals(listOf(Watch("Video A", "Channel A", 213, Moment(0, 1_000_000_000L))), reports)
    }

    @Test
    fun `ads under the video's title are one watch with the longest length`() {
        // From a real log: ads of 30, 6, 6, 10 and 18 seconds, then the 896 second video, all under one title.
        val lengths = listOf(30L to 0, 6L to 30, 6L to 37, 10L to 43, 18L to 54, 896L to 61)
        lengths.forEachIndexed { i, (seconds, start) ->
            val end = lengths.getOrNull(i + 1)?.second ?: 200
            play(start, end, video(seconds = seconds))
        }
        at(300, null)
        assertEquals(1, reports.size)
        assertEquals(896, reports.single().durationSec)
        assertEquals(0L, reports.single().start.elapsedMs)
    }

    @Test
    fun `a long video is reported after two minutes without waiting for it to end`() {
        play(0, 119, video(seconds = 3000))
        assertTrue(reports.isEmpty())
        play(120, 130, video(seconds = 3000))
        assertEquals(1, reports.size)
        play(130, 3000, video(seconds = 3000))
        at(3100, null)
        assertEquals("still one report for the whole video", 1, reports.size)
    }

    @Test
    fun `the same video replayed within a minute is one report`() {
        play(0, 30, video(seconds = 30))
        at(31, null)
        play(60, 90, video(seconds = 30))
        at(91, null)
        at(200, null)
        assertEquals(1, reports.size)
    }

    @Test
    fun `the same video replayed after a minute is reported again`() {
        play(0, 30, video(seconds = 30))
        at(31, null)
        at(100, null)
        play(200, 230, video(seconds = 30))
        at(231, null)
        at(300, null)
        assertEquals(2, reports.size)
        assertEquals(listOf(0L, 200_000L), reports.map { it.start.elapsedMs })
    }

    @Test
    fun `a pause and resume is the same watch`() {
        play(0, 20, video())
        at(21, null)
        at(50, null)
        play(51, 70, video())
        at(71, null)
        at(200, null)
        assertEquals(1, reports.size)
    }

    @Test
    fun `quick successive Shorts each count once`() {
        play(0, 9, video("Short 1", seconds = 20))
        play(10, 19, video("Short 2", seconds = 20))
        // Skipped through in 3 seconds, so it doesn't count.
        play(20, 23, video("Short 3", seconds = 20))
        play(24, 33, video("Short 4", seconds = 20))
        at(34, null)
        at(200, null)
        assertEquals(listOf("Short 1", "Short 2", "Short 4"), reports.map { it.title })
    }

    @Test
    fun `reports come out oldest first`() {
        play(0, 10, video("First"))
        play(11, 21, video("Second"))
        at(22, null)
        at(200, null)
        assertEquals(listOf("First", "Second"), reports.map { it.title })
    }

    @Test
    fun `a different channel with the same title is a different video`() {
        play(0, 10, video(channel = "One"))
        play(11, 21, video(channel = "Two"))
        at(22, null)
        at(200, null)
        assertEquals(listOf("One", "Two"), reports.map { it.channel })
    }

    @Test
    fun `nothing counts while parent mode is on`() {
        var t = 0
        while (t < 60) {
            at(t, video(), suppressed = true)
            t += 3
        }
        at(61, null)
        at(200, null)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `a video that was playing when parent mode started is not reported`() {
        play(0, 20, video())
        at(21, video(), suppressed = true)
        at(22, null, suppressed = true)
        at(200, null)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `a video the child watched before parent mode started is still reported`() {
        play(0, 200, video(seconds = 1000))
        assertEquals(1, reports.size)
        at(201, video(), suppressed = true)
        at(400, null)
        assertEquals(1, reports.size)
    }

    @Test
    fun `a blank title is not a video`() {
        play(0, 30, video(title = "  "))
        at(31, null)
        at(200, null)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `a long gap between updates is not counted as viewing`() {
        // Ten minutes with no update (the listener was not running) is worth at most 15 seconds of play.
        val strict = WatchTracker(WatchTracker.Rules(minPlayMs = 20_000))
        strict.update(Moment(0, 0), video())
        strict.update(Moment(600_000, 600_000), video())
        strict.update(Moment(601_000, 601_000), null)
        assertTrue(strict.update(Moment(700_000, 700_000), null).isEmpty())
    }

    @Test
    fun `finish reports what played enough without waiting`() {
        play(0, 12, video(seconds = 300))
        play(13, 15, video("Skipped", seconds = 300))
        val out = tracker.finish(Moment(16_000, 1_000_016_000))
        assertEquals(listOf("Video A"), out.map { it.title })
    }

    @Test
    fun `a video with no length is reported without one`() {
        play(0, 10, Playing("Live", "Channel", 0))
        at(11, null)
        at(100, null)
        assertEquals(null, reports.single().durationSec)
    }
}
