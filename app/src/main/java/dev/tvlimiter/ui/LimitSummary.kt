package dev.tvlimiter.ui

import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.LimitEvaluator
import java.time.DayOfWeek

/** How one limit stands today, as the home list and the edit screen show it. */
internal data class LimitSummary(val status: String, val fraction: Float?, val exhausted: Boolean, val added: String?) {
    companion object {
        fun of(limit: Limit, usage: DayUsage, day: DayOfWeek): LimitSummary {
            val used = formatMinutes(usage.usedSeconds(limit))
            val added = usage.addedTime(limit.id)
            val minutes = limit.minutesOn(day)
            val remaining = LimitEvaluator.remainingSeconds(limit, usage, day)
            return when {
                !limit.enabled -> LimitSummary("Off", null, false, added)
                remaining == null && minutes != null -> LimitSummary("$used used, no limit for the rest of today", null, false, added)
                remaining == null -> LimitSummary("No limit today, $used used", null, false, added)
                remaining <= 0 -> LimitSummary("Time's up, $used used", 0f, true, added)
                else -> {
                    val allowed = ((minutes ?: 0) + (usage.grantMinutesByLimitId[limit.id] ?: 0)) * 60f
                    LimitSummary("${formatMinutes(remaining)} left", remaining / allowed, false, added)
                }
            }
        }
    }
}
