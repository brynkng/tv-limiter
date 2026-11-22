package dev.tvlimiter.ui

/**
 * The seven day values being edited, Monday first. Left and right step by 15 minutes. Stepping
 * below zero means "no limit" that day, and stepping right from "no limit" starts at 15 minutes.
 */
class ScheduleEditorState(minutesByDay: List<Int?>) {
    private val days = minutesByDay.toMutableList()

    val minutes: List<Int?> get() = days.toList()

    fun right(day: Int) {
        days[day] = when (val current = days[day]) {
            null -> STEP
            else -> minOf(current + STEP, MAX_MINUTES)
        }
    }

    fun left(day: Int) {
        days[day] = when (val current = days[day]) {
            null, 0 -> null
            else -> maxOf(current - STEP, 0)
        }
    }

    fun copyMondayToAllDays() {
        for (day in 1..6) days[day] = days[0]
    }

    fun copyMondayToWeekdays() {
        for (day in 1..4) days[day] = days[0]
    }

    fun copySaturdayToWeekend() {
        days[6] = days[5]
    }

    companion object {
        const val STEP = 15
        const val MAX_MINUTES = 24 * 60

        fun describe(minutes: Int?): String = when (minutes) {
            null -> "No limit"
            0 -> "Blocked all day"
            else -> formatMinutes(minutes * 60L)
        }
    }
}
