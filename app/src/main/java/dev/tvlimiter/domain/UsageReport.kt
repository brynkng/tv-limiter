package dev.tvlimiter.domain

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Every finished day, oldest first. Today lives in the usage file until midnight. */
@Serializable
data class UsageHistory(val days: List<DayUsage> = emptyList()) {

    /** Adds a finished day, replacing any earlier copy of the same date, and drops days older than [KEEP_DAYS]. */
    fun withDay(day: DayUsage): UsageHistory {
        if (day.date.isEmpty()) return this
        val kept = (days.filter { it.date != day.date } + day).sortedBy { it.date }
        return UsageHistory(kept.takeLast(KEEP_DAYS))
    }

    companion object {
        /** A little over a year, so the months view can always show a full year back. */
        const val KEEP_DAYS = 400
    }
}

enum class ReportRange(val periods: Int) {
    DAYS(7),
    WEEKS(8),
    MONTHS(6),
}

/** One bar on the report: a day, a week starting Monday, or a calendar month. */
data class ReportPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val title: String,
    val tvSeconds: Long,
    val secondsByPackage: Map<String, Long>,
    /** Days in the period that have any record, to tell "no data yet" from "watched nothing". */
    val daysWithData: Int,
) {
    /** Apps by time used, most first. */
    val topApps: List<Pair<String, Long>>
        get() = secondsByPackage.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }
}

/** Groups daily usage into the periods the report shows. */
object UsageReport {

    fun periods(history: UsageHistory, today: DayUsage, todayDate: LocalDate, range: ReportRange): List<ReportPeriod> {
        val byDate = (history.days + today).filter { it.date.isNotEmpty() }.associateBy { LocalDate.parse(it.date) }
        val starts = when (range) {
            ReportRange.DAYS -> List(range.periods) { todayDate.minusDays((range.periods - 1 - it).toLong()) }
            ReportRange.WEEKS -> {
                val thisWeek = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                List(range.periods) { thisWeek.minusWeeks((range.periods - 1 - it).toLong()) }
            }
            ReportRange.MONTHS -> {
                val thisMonth = todayDate.withDayOfMonth(1)
                List(range.periods) { thisMonth.minusMonths((range.periods - 1 - it).toLong()) }
            }
        }
        return starts.map { start ->
            val end = when (range) {
                ReportRange.DAYS -> start
                ReportRange.WEEKS -> start.plusDays(6)
                ReportRange.MONTHS -> start.plusMonths(1).minusDays(1)
            }
            val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.mapNotNull { byDate[it] }.toList()
            val byPackage = mutableMapOf<String, Long>()
            days.forEach { day -> day.secondsByPackage.forEach { (pkg, s) -> byPackage[pkg] = (byPackage[pkg] ?: 0) + s } }
            ReportPeriod(
                start = start,
                end = end,
                label = label(range, start, todayDate),
                title = title(range, start, end, todayDate),
                tvSeconds = days.sumOf { it.tvSeconds },
                secondsByPackage = byPackage,
                daysWithData = days.size,
            )
        }
    }

    /** The [count] apps used most across all [periods], most first, for the chart's colors. */
    fun topPackages(periods: List<ReportPeriod>, count: Int): List<String> {
        val totals = mutableMapOf<String, Long>()
        periods.forEach { p -> p.secondsByPackage.forEach { (pkg, s) -> totals[pkg] = (totals[pkg] ?: 0) + s } }
        return totals.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(count).map { it.key }
    }

    /**
     * A period's bar split into one segment per app in [packages], then everything else.
     * The segments add up to the period's TV time; app times that overlap or exceed it are
     * scaled down to fit, so the bar's height always matches the total shown.
     */
    fun segments(period: ReportPeriod, packages: List<String>): List<Long> {
        val apps = packages.map { period.secondsByPackage[it] ?: 0L }
        val total = period.tvSeconds
        val named = apps.sum()
        if (named > total) {
            if (named == 0L) return apps + 0L
            val scaled = apps.map { it * total / named }
            return scaled + (total - scaled.sum())
        }
        return apps + (total - named)
    }

    private val dayShort = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    private val monthLong = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    private fun label(range: ReportRange, start: LocalDate, today: LocalDate): String = when (range) {
        ReportRange.DAYS -> if (start == today) "Today" else start.format(dayShort)
        ReportRange.WEEKS -> start.format(monthDay)
        ReportRange.MONTHS -> start.format(monthShort)
    }

    private fun title(range: ReportRange, start: LocalDate, end: LocalDate, today: LocalDate): String = when (range) {
        ReportRange.DAYS -> if (start == today) "Today" else start.format(dayLong)
        ReportRange.WEEKS -> if (!today.isBefore(start) && !today.isAfter(end)) "This week" else "Week of ${start.format(monthDay)}"
        ReportRange.MONTHS -> if (start.month == today.month && start.year == today.year) "This month" else start.format(monthLong)
    }
}
