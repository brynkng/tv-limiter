package dev.tvlimiter.domain

import java.time.DayOfWeek

/** A covering limit and how much of it is left. A null remaining time means it can't run out today. */
data class LimitStatus(val limit: Limit, val remainingSeconds: Long?) {
    val isExhausted: Boolean get() = remainingSeconds != null && remainingSeconds <= 0
}

data class Evaluation(val covering: List<LimitStatus>) {
    /** Every covering limit at or below zero, tightest first, with app limits winning ties. */
    val exhausted: List<LimitStatus> = covering
        .filter { it.isExhausted }
        .sortedWith(compareBy<LimitStatus> { it.remainingSeconds }.thenBy { it.limit.isWholeTv })

    /** The limit the block screen names, if any. */
    val blocking: LimitStatus? get() = exhausted.firstOrNull()
}

/**
 * Every minute counts against every limit that covers the app, and the limit with the
 * least time left decides when to block.
 */
object LimitEvaluator {

    fun remainingSeconds(limit: Limit, usage: DayUsage, day: DayOfWeek): Long? {
        if (!limit.enabled) return null
        if (limit.id in usage.restOfDayLimitIds) return null
        val minutes = limit.minutesOn(day) ?: return null
        val granted = usage.grantMinutesByLimitId[limit.id] ?: 0
        return (minutes + granted) * 60L - usage.usedSeconds(limit)
    }

    fun evaluate(limits: List<Limit>, usage: DayUsage, day: DayOfWeek, packageName: String): Evaluation =
        Evaluation(
            limits
                .filter { it.covers(packageName) }
                .map { LimitStatus(it, remainingSeconds(it, usage, day)) },
        )
}
