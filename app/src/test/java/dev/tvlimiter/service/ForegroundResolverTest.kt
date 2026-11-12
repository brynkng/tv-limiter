package dev.tvlimiter.service

import dev.tvlimiter.service.AppEvent.Type.PAUSED
import dev.tvlimiter.service.AppEvent.Type.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundResolverTest {
    @Test
    fun `the last resumed app is in front`() {
        assertEquals("b", ForegroundResolver.resolve(null, listOf(AppEvent("a", RESUMED, 1), AppEvent("b", RESUMED, 2))))
    }

    @Test
    fun `a pause with nothing resumed after it leaves nothing in front`() {
        assertNull(ForegroundResolver.resolve(null, listOf(AppEvent("a", RESUMED, 1), AppEvent("a", PAUSED, 2))))
    }

    @Test
    fun `the old app pausing after the new one resumed does not clear the new one`() {
        val events = listOf(AppEvent("a", RESUMED, 1), AppEvent("b", RESUMED, 2), AppEvent("a", PAUSED, 3))
        assertEquals("b", ForegroundResolver.resolve(null, events))
    }

    @Test
    fun `an empty window keeps the previous app`() {
        assertEquals("a", ForegroundResolver.resolve("a", emptyList()))
    }

    @Test
    fun `with no previous state a look back window finds the open app`() {
        assertEquals("a", ForegroundResolver.resolve(null, listOf(AppEvent("x", RESUMED, 1), AppEvent("a", RESUMED, 5))))
    }

    @Test
    fun `events are applied in time order`() {
        assertEquals("b", ForegroundResolver.resolve(null, listOf(AppEvent("b", RESUMED, 9), AppEvent("a", RESUMED, 1))))
    }
}
