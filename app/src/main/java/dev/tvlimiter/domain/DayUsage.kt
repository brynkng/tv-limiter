package dev.tvlimiter.domain

import kotlinx.serialization.Serializable

/** Everything counted for one local date. Grants belong to the day and vanish at midnight. */
@Serializable
data class DayUsage(
    val date: String,
    val secondsByPackage: Map<String, Long> = emptyMap(),
    val tvSeconds: Long = 0,
    val grantMinutesByLimitId: Map<String, Int> = emptyMap(),
    val restOfDayLimitIds: Set<String> = emptySet(),
) {
    fun secondsFor(packageName: String): Long = secondsByPackage[packageName] ?: 0

    fun usedSeconds(limit: Limit): Long = when (val scope = limit.scope) {
        is LimitScope.App -> secondsFor(scope.packageName)
        LimitScope.WholeTv -> tvSeconds
    }

    /** What the owner added to a limit today, such as "+45 min", or null when nothing was added. */
    fun addedTime(limitId: String): String? {
        val minutes = grantMinutesByLimitId[limitId] ?: 0
        return when {
            limitId in restOfDayLimitIds -> "the rest of today"
            minutes > 0 -> "+$minutes min"
            else -> null
        }
    }
}

/** The override amounts the owner can pick from after entering the PIN. */
enum class GrantChoice(val minutes: Int?, val label: String) {
    PLUS_15(15, "+15 min"),
    PLUS_30(30, "+30 min"),
    PLUS_60(60, "+60 min"),
    REST_OF_DAY(null, "Rest of today"),
}
