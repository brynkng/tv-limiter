package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PositionsTest {
    @Test
    fun `the position moves on from the last update by the time since, at the playback speed`() {
        assertEquals(40_000L, Positions.now(30_000, updatedAtMs = 100_000, speed = 1f, nowMs = 110_000, lengthMs = 600_000))
        assertEquals(50_000L, Positions.now(30_000, updatedAtMs = 100_000, speed = 2f, nowMs = 110_000, lengthMs = 600_000))
    }

    @Test
    fun `a session that gives no position gives none`() {
        assertNull(Positions.now(-1, 100_000, 1f, 110_000, 600_000))
    }

    @Test
    fun `a missing update time or speed uses the position as given or normal speed, and it never passes the end`() {
        assertEquals(30_000L, Positions.now(30_000, updatedAtMs = 0, speed = 1f, nowMs = 110_000, lengthMs = 600_000))
        assertEquals(40_000L, Positions.now(30_000, updatedAtMs = 100_000, speed = 0f, nowMs = 110_000, lengthMs = 600_000))
        assertEquals(600_000L, Positions.now(595_000, updatedAtMs = 100_000, speed = 1f, nowMs = 120_000, lengthMs = 600_000))
    }
}
