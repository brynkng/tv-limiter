package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBlockerTest {
    private class MemoryStore : BlockCacheStore {
        var entries: List<CachedBlock> = emptyList()
        override fun load() = entries
        override fun save(entries: List<CachedBlock>) {
            this.entries = entries
        }
    }

    private class FakeActions : BlockActions {
        var pauses = 0
        val shown = mutableListOf<BlockedVideo>()
        var showing = false
        override fun pause() {
            pauses++
        }
        override fun show(video: BlockedVideo) {
            shown += video
            showing = true
        }
        override fun isShowing() = showing
    }

    private var now = 1_000_000L
    private var parentMode = false
    private val answers = mutableMapOf<String, Fetch>()
    private val asked = mutableListOf<String>()
    private val actions = FakeActions()
    private val allowances = Allowances()
    private val cache = BlockCache(MemoryStore(), { now })
    private val blocker = VideoBlocker(
        cache,
        allowances,
        { parentMode },
        { id ->
            asked += id
            answers[id] ?: Fetch.Unreachable
        },
        actions,
    )

    private val castle = Playing("Haunted Castle", "Spooky TV", 600_000)
    private val castleKey = VideoKey.of(castle)
    private fun outcome(name: String, kind: OverKind? = null, value: String? = null, level: String? = null, decided: Boolean = true) =
        WatchOutcome(decided, name, kind, value, level, null)
    private val over = outcome("over", OverKind.VALUE, "scary", "a_lot")

    @Test
    fun `an over outcome in a report reply blocks the video that is playing`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", over, now)
        assertEquals(1, actions.pauses)
        assertEquals(1, actions.shown.size)
        val shown = actions.shown.single()
        assertEquals("w1", shown.watchEventId)
        assertEquals("This video has a lot of scary or creepy parts.", shown.reason)
    }

    @Test
    fun `near never blocks`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", outcome("near", OverKind.VALUE, "scary", "some"), now)
        blocker.onPlaying(castle)
        assertEquals(0, actions.pauses)
        assertTrue(actions.shown.isEmpty())
    }

    @Test
    fun `within and not checked never block`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", outcome("within"), now)
        blocker.onReported(castleKey, "w2", outcome("not_checked"), now)
        blocker.onReported(castleKey, "w3", outcome("unidentified"), now)
        assertTrue(actions.shown.isEmpty())
    }

    @Test
    fun `parent mode never blocks, even a video known to be over`() {
        cache.put(castleKey, over, "w1")
        parentMode = true
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", over, now)
        assertEquals(0, actions.pauses)
        assertTrue(actions.shown.isEmpty())
    }

    @Test
    fun `playing a video known to be over blocks at once from the cache`() {
        cache.put(castleKey, over, "w1")
        blocker.onPlaying(castle)
        assertEquals(1, actions.pauses)
        assertEquals("w1", actions.shown.single().watchEventId)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `pressing play again behind the cover pauses again without a second screen`() {
        cache.put(castleKey, over, "w1")
        blocker.onPlaying(castle)
        blocker.onPlaying(castle)
        assertEquals(2, actions.pauses)
        assertEquals(1, actions.shown.size)
    }

    @Test
    fun `after the cover is removed, playing the same video covers it again`() {
        cache.put(castleKey, over, "w1")
        blocker.onPlaying(castle)
        actions.showing = false
        blocker.onPlaying(castle)
        assertEquals(2, actions.shown.size)
    }

    @Test
    fun `a different video is not blocked by the cache`() {
        cache.put(castleKey, over, "w1")
        blocker.onPlaying(Playing("Cute Cats", "Spooky TV", 600_000))
        blocker.onPlaying(Playing("Haunted Castle", "Spooky TV", 30_000))
        assertTrue(actions.shown.isEmpty())
    }

    @Test
    fun `a cached block is forgotten after 24 hours`() {
        cache.put(castleKey, over, "w1")
        now += BlockCache.TTL_MS + 1
        blocker.onPlaying(castle)
        assertTrue(actions.shown.isEmpty())
    }

    @Test
    fun `allowing the channel until the TV sleeps lifts the block, and clearing brings it back`() {
        cache.put(castleKey, over, "w1")
        allowances.allowChannel(castleKey)
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", over, now)
        assertTrue(actions.shown.isEmpty())
        // The TV slept.
        allowances.clear()
        blocker.onPlaying(castle)
        assertEquals(1, actions.shown.size)
    }

    @Test
    fun `allowing the video lifts the block for that video only`() {
        cache.put(castleKey, over, "w1")
        val other = VideoKey.of("Haunted Castle 2", "Spooky TV", 500)
        cache.put(other, over, "w2")
        allowances.allowVideo(castleKey)
        blocker.onPlaying(castle)
        assertTrue(actions.shown.isEmpty())
        blocker.onPlaying(Playing("Haunted Castle 2", "Spooky TV", 500_000))
        assertEquals(1, actions.shown.size)
    }

    @Test
    fun `an undecided watch is asked about every three seconds`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", null, now)
        blocker.poll(now)
        blocker.poll(now + 1_000)
        blocker.poll(now + 2_999)
        assertEquals(listOf("w1"), asked)
        blocker.poll(now + 3_000)
        assertEquals(listOf("w1", "w1"), asked)
    }

    @Test
    fun `an outcome that arrives while polling blocks the video, and polling stops`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", null, now)
        answers["w1"] = Fetch.Got(outcome("not_checked", decided = false))
        blocker.poll(now)
        assertTrue(actions.shown.isEmpty())
        assertEquals(1, blocker.pendingCount())
        answers["w1"] = Fetch.Got(over)
        blocker.poll(now + 3_000)
        assertEquals(1, actions.shown.size)
        assertEquals(0, blocker.pendingCount())
        blocker.poll(now + 6_000)
        assertEquals(2, asked.size)
        assertNotNull(cache.find(castleKey))
    }

    @Test
    fun `a video decided to be fine later is never blocked`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", null, now)
        answers["w1"] = Fetch.Got(outcome("within"))
        blocker.poll(now)
        assertTrue(actions.shown.isEmpty())
        assertEquals(0, blocker.pendingCount())
        assertNull(cache.find(castleKey))
    }

    @Test
    fun `polling gives up after 90 seconds and nothing is blocked`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", null, now)
        var t = now
        while (t <= now + 90_000) {
            blocker.poll(t)
            t += 3_000
        }
        val askedWhileWaiting = asked.size
        assertEquals(31, askedWhileWaiting)
        blocker.poll(now + 90_001)
        blocker.poll(now + 120_000)
        assertEquals(askedWhileWaiting, asked.size)
        assertEquals(0, blocker.pendingCount())
        assertTrue(actions.shown.isEmpty())
    }

    @Test
    fun `an unreachable backend fails open`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", null, now)
        answers["w1"] = Fetch.Unreachable
        for (i in 0..30) blocker.poll(now + i * 3_000L)
        assertTrue(actions.shown.isEmpty())
        assertEquals(0, actions.pauses)
    }

    @Test
    fun `a watch the backend no longer knows is dropped`() {
        blocker.onReported(castleKey, "w1", null, now)
        answers["w1"] = Fetch.Gone
        blocker.poll(now)
        assertEquals(0, blocker.pendingCount())
    }

    @Test
    fun `a verdict that arrives after the child moved on is remembered but does not block the new video`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", null, now)
        blocker.onPlaying(Playing("Cute Cats", "Cat TV", 300_000))
        answers["w1"] = Fetch.Got(over)
        blocker.poll(now)
        assertTrue(actions.shown.isEmpty())
        assertNotNull(cache.find(castleKey))
    }

    @Test
    fun `a parent lifting a video forgets its block`() {
        cache.put(castleKey, over, "w1")
        blocker.onPlaying(castle)
        blocker.lifted(castleKey)
        assertNull(cache.find(castleKey))
        assertNull(blocker.blocked)
    }

    @Test
    fun `a second watch of the same video replaces the first one being asked about`() {
        blocker.onReported(castleKey, "w1", null, now)
        blocker.onReported(castleKey, "w2", null, now + 1_000)
        assertEquals(1, blocker.pendingCount())
        blocker.poll(now + 1_000)
        assertEquals(listOf("w2"), asked)
    }

    @Test
    fun `a new video blocked while the screen is up replaces what it shows`() {
        cache.put(castleKey, over, "w1")
        val other = VideoKey.of("Zombie Mall", "Spooky TV", 300)
        cache.put(other, over, "w2")
        blocker.onPlaying(castle)
        blocker.onPlaying(Playing("Zombie Mall", "Spooky TV", 300_000))
        assertEquals(2, actions.shown.size)
        assertFalse(actions.shown[0].key.sameVideo(actions.shown[1].key))
    }

    @Test
    fun `a ban made while a fine video plays stops it at the next check`() {
        val within = outcome("within")
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", within, now)
        assertTrue(blocker.hasWork())

        answers["w1"] = Fetch.Got(outcome("over", OverKind.CHANNEL_BAN))
        now += VideoBlocker.RECHECK_EVERY_MS - 1
        blocker.poll(now)
        assertEquals("not asked again before the recheck is due", 0, asked.size)

        now += 1
        blocker.poll(now)
        assertEquals(listOf("w1"), asked)
        assertEquals(1, actions.shown.size)
        assertTrue(actions.pauses > 0)
    }

    @Test
    fun `a fine video is no longer rechecked once another video plays`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", outcome("within"), now)
        blocker.onPlaying(Playing("Cake Time", "Bakers", 300_000))
        now += VideoBlocker.RECHECK_EVERY_MS
        blocker.poll(now)
        assertTrue(asked.isEmpty())
        assertFalse(blocker.hasWork())
    }

    @Test
    fun `a recheck that still finds the video fine keeps it playing and asks again later`() {
        blocker.onPlaying(castle)
        blocker.onReported(castleKey, "w1", outcome("within"), now)
        answers["w1"] = Fetch.Got(outcome("near"))
        now += VideoBlocker.RECHECK_EVERY_MS
        blocker.poll(now)
        now += VideoBlocker.RECHECK_EVERY_MS
        blocker.poll(now)
        assertEquals(listOf("w1", "w1"), asked)
        assertTrue(actions.shown.isEmpty())
    }
}
