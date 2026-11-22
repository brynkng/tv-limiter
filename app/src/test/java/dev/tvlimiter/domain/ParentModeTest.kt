package dev.tvlimiter.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentModeTest {
    @Test
    fun `starts off, turns on, and turns off again`() {
        val mode = ParentMode()
        assertFalse(mode.active)
        mode.start()
        assertTrue(mode.active)
        mode.end()
        assertFalse(mode.active)
    }
}
