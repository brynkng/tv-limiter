package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasskeyStateTest {
    @Test
    fun `the code is submitted on the sixth press and the entry starts over`() {
        val state = PasskeyState()
        val presses = listOf(Arrow.UP, Arrow.UP, Arrow.DOWN, Arrow.LEFT, Arrow.RIGHT)
        presses.forEach { assertNull(state.press(it)) }
        assertEquals(5, state.length)
        assertEquals("UUDLRL", state.press(Arrow.LEFT))
        assertEquals(0, state.length)
    }

    @Test
    fun `clear drops a partial entry`() {
        val state = PasskeyState()
        state.press(Arrow.UP)
        state.press(Arrow.RIGHT)
        state.clear()
        assertEquals(0, state.length)
        repeat(5) { state.press(Arrow.DOWN) }
        assertEquals("DDDDDD", state.press(Arrow.DOWN))
    }
}
