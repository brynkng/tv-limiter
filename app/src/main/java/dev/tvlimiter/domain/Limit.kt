package dev.tvlimiter.domain

import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a limit covers: one app, or every non-exempt app on the TV. */
@Serializable
sealed interface LimitScope {
    @Serializable
    @SerialName("app")
    data class App(val packageName: String) : LimitScope

    @Serializable
    @SerialName("tv")
    data object WholeTv : LimitScope
}

/**
 * A daily allowance with one value per weekday, Monday first.
 * A null day means "no limit" on that day.
 */
@Serializable
data class Limit(
    val id: String,
    val scope: LimitScope,
    val minutesByDay: List<Int?>,
    val enabled: Boolean = true,
) {
    init {
        require(minutesByDay.size == 7) { "minutesByDay needs 7 entries, got ${minutesByDay.size}" }
    }

    fun minutesOn(day: DayOfWeek): Int? = minutesByDay[day.value - 1]

    fun covers(packageName: String): Boolean = when (scope) {
        is LimitScope.App -> scope.packageName == packageName
        LimitScope.WholeTv -> true
    }

    val isWholeTv: Boolean get() = scope == LimitScope.WholeTv

    companion object {
        const val WHOLE_TV_ID = "tv"

        fun appLimitId(packageName: String) = "app:$packageName"

        fun defaultWholeTv() = Limit(
            id = WHOLE_TV_ID,
            scope = LimitScope.WholeTv,
            minutesByDay = List(7) { 180 },
            enabled = false,
        )

        fun newAppLimit(packageName: String) = Limit(
            id = appLimitId(packageName),
            scope = LimitScope.App(packageName),
            minutesByDay = List(7) { 60 },
        )
    }
}
