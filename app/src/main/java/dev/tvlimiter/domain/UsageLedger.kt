package dev.tvlimiter.domain

import java.time.LocalDate

/** Pure day-usage arithmetic. Each call rolls over to a fresh day first when the date has changed. */
object UsageLedger {

    fun rollover(usage: DayUsage, today: LocalDate): DayUsage {
        val key = today.toString()
        return if (usage.date == key) usage else DayUsage(date = key)
    }

    fun charge(usage: DayUsage, packageName: String, seconds: Long, today: LocalDate): DayUsage {
        val current = rollover(usage, today)
        if (seconds <= 0) return current
        return current.copy(
            secondsByPackage = current.secondsByPackage + (packageName to current.secondsFor(packageName) + seconds),
            tvSeconds = current.tvSeconds + seconds,
        )
    }

    fun grant(usage: DayUsage, limitId: String, choice: GrantChoice, today: LocalDate): DayUsage {
        val current = rollover(usage, today)
        val minutes = choice.minutes ?: return current.copy(restOfDayLimitIds = current.restOfDayLimitIds + limitId)
        val total = (current.grantMinutesByLimitId[limitId] ?: 0) + minutes
        return current.copy(grantMinutesByLimitId = current.grantMinutesByLimitId + (limitId to total))
    }
}
