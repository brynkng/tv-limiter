package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSpansTest {
    private val tracker = WatchTracker()
    private val reports = mutableListOf<Watch>()
    private val updates = mutableListOf<SpanUpdate>()

    /** An item of [lengthSec] with the player at [posSec]. */
    private fun item(posSec: Double?, lengthSec: Long = 3000, title: String = "Video A") =
        Playing(title, "Channel A", lengthSec * 1000, positionMs = posSec?.let { (it * 1000).toLong() })

    private fun at(t: Int, playing: Playing?) {
        reports += tracker.update(Moment(t * 1000L, 1_000_000_000L + t * 1000L), playing)
        updates += tracker.takeSpanUpdates()
    }

    /** Plays from clock second [from] to [to], the player moving on with the clock from [startPos], updated every 3 seconds. */
    private fun play(from: Int, to: Int, startPos: Double, lengthSec: Long = 3000, title: String = "Video A") {
        var t = from
        while (t <= to) {
            at(t, item(startPos + (t - from), lengthSec, title))
            t += 3
        }
    }

    private fun spansOf(update: SpanUpdate) = update.spans.map { it.startSec to it.endSec }

    @Test
    fun `steady play is one span from where it began, and the first report carries it`() {
        play(0, 9, startPos = 100.0)
        assertEquals(1, reports.size)
        // Reported once 5 seconds had played, at the third update.
        assertEquals(listOf(Span(100, 106)), reports[0].spans)
    }

    @Test
    fun `a watch whose session gives no position has no spans and sends no updates`() {
        var t = 0
        while (t <= 120) {
            at(t, item(null))
            t += 3
        }
        assertNull(reports[0].spans)
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `the spans go out about every 30 seconds of play, and as soon as a minute has been played`() {
        play(0, 90, startPos = 0.0)
        assertEquals(listOf(listOf(0 to 36), listOf(0 to 60), listOf(0 to 90)), updates.map { spansOf(it) })
    }

    @Test
    fun `a pause and a resume carry on as one span, and a jump forward or back starts a new one`() {
        play(0, 30, startPos = 0.0)
        at(33, null)
        at(36, null)
        // Resumed where it stopped.
        play(39, 60, startPos = 30.0)
        // Jumped forward 10 minutes.
        play(63, 90, startPos = 700.0)
        // Jumped back to near the start, over what was already played.
        play(93, 99, startPos = 5.0)
        tracker.finish(Moment(100_000, 1_000_100_000))
        updates += tracker.takeSpanUpdates()
        assertEquals(listOf(0 to 51, 700 to 727), spansOf(updates.last()))
    }

    @Test
    fun `an ad inside the video with a length of its own adds no span`() {
        play(0, 30, startPos = 200.0)
        // A 30 second mid-roll ad: its own length, its own positions.
        var t = 33
        while (t <= 60) {
            at(t, item(posSec = (t - 33).toDouble(), lengthSec = 30))
            t += 3
        }
        play(63, 90, startPos = 230.0)
        tracker.finish(Moment(100_000, 1_000_100_000))
        updates += tracker.takeSpanUpdates()
        assertEquals(listOf(200 to 257), spansOf(updates.last()))
    }

    @Test
    fun `spans played under an ad's length are dropped once the video's own length shows`() {
        // A 20 second pre-roll ad, then the 50 minute video.
        play(0, 9, startPos = 0.0, lengthSec = 20)
        play(12, 30, startPos = 0.0, lengthSec = 3000)
        tracker.finish(Moment(100_000, 1_000_100_000))
        updates += tracker.takeSpanUpdates()
        val last = spansOf(updates.last())
        assertEquals(1, last.size)
        assertEquals(0, last[0].first)
        assertTrue("only the video's own play counts, not the ad's 9 seconds", last[0].second in 17..19)
    }

    @Test
    fun `a watch that ends goes out with what was played since the last update`() {
        play(0, 20, startPos = 500.0)
        val before = updates.size
        // Nothing plays for a minute: the watch is over.
        at(30, null)
        at(95, null)
        assertEquals(before + 1, updates.size)
        assertEquals(listOf(500 to 518), spansOf(updates.last()))
    }

    @Test
    fun `finishing the tracker sends the spans too`() {
        play(0, 20, startPos = 0.0)
        val before = updates.size
        tracker.finish(Moment(25_000, 1_000_025_000))
        updates += tracker.takeSpanUpdates()
        assertEquals(before + 1, updates.size)
    }

    @Test
    fun `a Short has no spans`() {
        val art = ShortArt("k1", "jpeg")
        var t = 0
        while (t <= 9) {
            reports += tracker.update(Moment(t * 1000L, 1_000_000_000L), Playing("", "", 30_000, art, positionMs = t * 1000L))
            t += 3
        }
        assertEquals(1, reports.size)
        assertNull(reports[0].spans)
        assertTrue(tracker.takeSpanUpdates().isEmpty())
    }

    @Test
    fun `a correction carries the spans too`() {
        // A long ad is reported with its own length first, then corrected to the video's.
        play(0, 9, startPos = 0.0, lengthSec = 90)
        play(12, 20, startPos = 0.0, lengthSec = 3000)
        val correction = reports.firstOrNull { it.correction }
        assertNotNull(correction)
        assertNotNull(correction!!.spans)
    }

    @Test
    fun `the first report and each update carry where the player is now, also after a seek back`() {
        play(0, 30, startPos = 1000.0)
        assertEquals(1006, reports[0].positionSec)
        // Seek back to the start of the video and play a little.
        play(33, 45, startPos = 20.0)
        tracker.finish(Moment(50_000, 1_000_050_000))
        updates += tracker.takeSpanUpdates()
        // The highest span is near 1030, but the player is at 32.
        assertEquals(32, updates.last().positionSec)
        assertEquals(listOf(20 to 32, 1000 to 1030), spansOf(updates.last()))
    }

    @Test
    fun `a watch whose session gives no position has none to send`() {
        var t = 0
        while (t <= 9) {
            at(t, item(null))
            t += 3
        }
        assertNull(reports[0].positionSec)
    }
}
