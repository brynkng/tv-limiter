package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockCacheTest {
    private class MemoryStore : BlockCacheStore {
        var entries: List<CachedBlock> = emptyList()
        override fun load() = entries
        override fun save(entries: List<CachedBlock>) {
            this.entries = entries
        }
    }

    private var now = 1_000_000L
    private val store = MemoryStore()
    private val cache = BlockCache(store, { now })
    private val scary = WatchOutcome(true, "over", OverKind.VALUE, "scary", "a_lot", null)

    private fun key(title: String = "Haunted Castle", channel: String = "Spooky TV", seconds: Int? = 600) = VideoKey.of(title, channel, seconds)

    @Test
    fun `a video found over a limit is found again`() {
        cache.put(key(), scary, "w1")
        val hit = cache.find(key())!!
        assertEquals("w1", hit.watchEventId)
        assertEquals(OverKind.VALUE, hit.overKind)
        assertEquals("scary", hit.value)
    }

    @Test
    fun `case, spacing and a length within three seconds are the same video`() {
        cache.put(key(), scary, "w1")
        assertNotNull(cache.find(key("  HAUNTED   castle ", "spooky tv", 603)))
        assertNotNull(cache.find(key(seconds = 597)))
        assertNull(cache.find(key(seconds = 604)))
        assertNull(cache.find(key(seconds = 20)))
    }

    @Test
    fun `another title or channel is another video`() {
        cache.put(key(), scary, "w1")
        assertNull(cache.find(key(title = "Haunted Castle 2")))
        assertNull(cache.find(key(channel = "Other")))
    }

    @Test
    fun `a video with no known length matches only another with none`() {
        cache.put(key(seconds = null), scary, "w1")
        assertNotNull(cache.find(key(seconds = null)))
        assertNull(cache.find(key(seconds = 600)))
    }

    @Test
    fun `an entry is kept for 24 hours and then forgotten`() {
        cache.put(key(), scary, "w1")
        now += BlockCache.TTL_MS - 1
        assertNotNull(cache.find(key()))
        now += 2
        assertNull(cache.find(key()))
    }

    @Test
    fun `putting a video again replaces its entry and renews its time`() {
        cache.put(key(), scary, "w1")
        now += BlockCache.TTL_MS - 10
        cache.put(key(), scary, "w2")
        now += 100
        assertEquals("w2", cache.find(key())!!.watchEventId)
        assertEquals(1, store.entries.size)
    }

    @Test
    fun `removing a video forgets it`() {
        cache.put(key(), scary, "w1")
        cache.remove(key())
        assertNull(cache.find(key()))
    }

    @Test
    fun `allowing a video or a channel lasts until it is cleared`() {
        val allowances = Allowances()
        assertFalse(allowances.allows(key()))
        allowances.allowVideo(key())
        assertTrue(allowances.allows(key(seconds = 601)))
        assertFalse(allowances.allows(key(title = "Another")))
        allowances.allowChannel(key())
        assertTrue(allowances.allows(key(title = "Another")))
        assertFalse(allowances.allows(key(title = "Another", channel = "Different")))
        allowances.clear()
        assertFalse(allowances.allows(key()))
    }
}
