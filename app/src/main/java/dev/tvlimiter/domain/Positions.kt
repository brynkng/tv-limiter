package dev.tvlimiter.domain

/** Where a media session says the player is. */
object Positions {
    /**
     * A playback state holds the position it had at its last update ([positionMs], measured on the elapsed clock at
     * [updatedAtMs]) and the speed. It does not tick, so the position now is that one moved on by the time since, at
     * the speed. Null when the session gives no position (a negative one). Never past the item's length.
     */
    fun now(positionMs: Long, updatedAtMs: Long, speed: Float, nowMs: Long, lengthMs: Long): Long? {
        if (positionMs < 0) return null
        val moved = if (updatedAtMs > 0 && nowMs >= updatedAtMs) ((nowMs - updatedAtMs) * (if (speed > 0f) speed else 1f)).toLong() else 0L
        val position = positionMs + moved
        return if (lengthMs > 0) position.coerceAtMost(lengthMs) else position
    }
}
