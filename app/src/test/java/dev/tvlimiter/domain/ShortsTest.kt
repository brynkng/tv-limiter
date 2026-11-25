package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsTest {
    private val tracker = WatchTracker()
    private val reports = mutableListOf<Watch>()

    private fun art(key: String) = ShortArt(key, "base64-$key")

    /** An untitled item with a picture, as YouTube's Shorts show in the media session. */
    private fun short(key: String = "a", seconds: Long = 30) = Playing("", "", seconds * 1000, art(key))

    private fun at(t: Int, playing: Playing?, suppressed: Boolean = false) {
        reports += tracker.update(Moment(t * 1000L, 1_000_000_000L + t * 1000L), playing, suppressed)
    }

    private fun play(from: Int, to: Int, playing: Playing?) {
        var t = from
        while (t < to) {
            at(t, playing)
            t += 3
        }
        at(to, playing)
    }

    @Test
    fun `an untitled item that has played 5 seconds and is 4 minutes or shorter is a Short, reported with its picture and length`() {
        play(0, 4, short(seconds = 37))
        assertTrue("not yet, it has played under 5 seconds", reports.isEmpty())
        play(5, 9, short(seconds = 37))
        val watch = reports.single()
        assertTrue(watch.isShort)
        assertEquals("", watch.title)
        assertEquals(37, watch.durationSec)
        assertEquals(art("a"), watch.short)
    }

    @Test
    fun `a Short is reported after about 5 seconds of play, not when it ends`() {
        play(0, 6, short())
        assertEquals(1, reports.size)
        play(7, 60, short())
        assertEquals("still one report while it plays on", 1, reports.size)
    }

    @Test
    fun `a regular video whose title arrives after 2 seconds is not a Short`() {
        val untitled = Playing("", "", 600_000, art("x"))
        at(0, untitled)
        at(2, untitled)
        play(3, 30, Playing("Real Video", "Real Channel", 600_000))
        assertEquals(listOf("Real Video"), reports.map { it.title })
        assertFalse(reports.single().isShort)
    }

    @Test
    fun `an untitled item longer than 4 minutes is not a Short`() {
        play(0, 30, short(seconds = 241))
        assertTrue(reports.isEmpty())
        play(40, 70, short("b", seconds = 240))
        assertEquals(1, reports.size)
    }

    @Test
    fun `an untitled item with no picture or no length is not a Short`() {
        play(0, 30, Playing("", "", 30_000))
        play(40, 70, Playing("", "", 0, art("a")))
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `each distinct Short is reported once, even when they play back to back`() {
        play(0, 6, short("a", 30))
        play(7, 13, short("b", 30))
        play(14, 20, short("c", 22))
        assertEquals(listOf("a", "b", "c"), reports.map { it.short!!.key })
        // Going back to one that already played, within the join gap, is the same watch.
        play(21, 27, short("b", 30))
        assertEquals(3, reports.size)
    }

    @Test
    fun `a Short skipped before 5 seconds is not reported`() {
        play(0, 3, short("a"))
        play(4, 11, short("b"))
        assertEquals(listOf("b"), reports.map { it.short!!.key })
    }

    @Test
    fun `a Short is never corrected to a longer length`() {
        play(0, 6, short("a", 30))
        play(7, 20, Playing("", "", 200_000, art("a")))
        assertTrue(reports.none { it.correction })
    }

    @Test
    fun `parent mode counts no Short`() {
        var t = 0
        while (t < 30) {
            at(t, short(), suppressed = true)
            t += 3
        }
        assertTrue(reports.isEmpty())
    }

    private val ana = Profile("p1", "Ana", AgeBand.OLDER, tubeGuardChildId = "c1", linkChosen = true)
    private val ben = Profile("p2", "Ben", AgeBand.OLDER, tubeGuardChildId = "c2", linkChosen = true)
    private val link = TubeGuardLink(
        LinkStatus.CONNECTED, "d", "t", listOf("Ana", "Ben"),
        listOf(TubeGuardChild("c1", "Ana", shortsBlocked = true), TubeGuardChild("c2", "Ben", shortsBlocked = false)), shortsBlocked = true,
    )

    @Test
    fun `the Shorts setting is the linked child's, else the whole TV's`() {
        assertTrue(Shorts.blocked("p1", listOf(ana, ben), link))
        assertFalse(Shorts.blocked("p2", listOf(ana, ben), link))
        assertTrue("no profile picked: the TV-wide setting", Shorts.blocked(null, listOf(ana, ben), link))
        assertTrue("a profile with no link: the TV-wide setting", Shorts.blocked("p3", listOf(ana, ben, Profile("p3", "Cy", AgeBand.OLDER)), link))
        assertFalse(Shorts.blocked(null, listOf(ana), link.copy(shortsBlocked = false)))
    }
}
