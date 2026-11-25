package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
    @Test
    fun `each failure waits twice as long, up to a limit`() {
        val backoff = Backoff(firstMs = 30_000, maxMs = 100_000)
        assertEquals(30_000, backoff.failure(0))
        assertEquals(60_000, backoff.failure(0))
        assertEquals(100_000, backoff.failure(0))
        assertEquals(100_000, backoff.failure(0))
    }

    @Test
    fun `sending is blocked until the wait is over`() {
        val backoff = Backoff(firstMs = 30_000)
        backoff.failure(nowMs = 1_000)
        assertEquals(30_000, backoff.waitLeftMs(1_000))
        assertEquals(10_000, backoff.waitLeftMs(21_000))
        assertEquals(0, backoff.waitLeftMs(31_000))
    }

    @Test
    fun `a success starts over`() {
        val backoff = Backoff(firstMs = 30_000)
        backoff.failure(0)
        backoff.failure(0)
        backoff.success()
        assertEquals(0, backoff.waitLeftMs(0))
        assertEquals(30_000, backoff.failure(0))
    }

    @Test
    fun `many failures do not overflow`() {
        val backoff = Backoff()
        repeat(200) { backoff.failure(0) }
        assertEquals(30 * 60_000L, backoff.failure(0))
    }
}
