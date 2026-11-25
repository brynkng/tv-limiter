package dev.tvlimiter.domain

/** How long to wait after uploads fail: [firstMs], doubling each time, up to [maxMs]. One success starts over. */
class Backoff(private val firstMs: Long = 30_000, private val maxMs: Long = 30 * 60_000) {
    private var failures = 0

    /** The elapsed time before which nothing should be sent, or 0 when there is no wait. */
    var blockedUntilMs = 0L
        private set

    /** Records a failure at [nowMs] and returns how long to wait. */
    fun failure(nowMs: Long): Long {
        val delay = minOf(maxMs, firstMs shl minOf(failures, 20))
        failures++
        blockedUntilMs = nowMs + delay
        return delay
    }

    fun success() {
        failures = 0
        blockedUntilMs = 0
    }

    fun waitLeftMs(nowMs: Long): Long = (blockedUntilMs - nowMs).coerceAtLeast(0)
}
