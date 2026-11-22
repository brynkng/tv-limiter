package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UsageReportTest {
    private val today = LocalDate.of(2026, 11, 22) // a Sunday

    private fun day(date: String, tv: Long, vararg apps: Pair<String, Long>) =
        DayUsage(date = date, tvSeconds = tv, secondsByPackage = apps.toMap())

    @Test
    fun `days show the last seven days ending today, with today's live usage`() {
        val history = UsageHistory(listOf(day("2026-11-21", 3_600, "yt" to 3_600)))
        val periods = UsageReport.periods(history, day("2026-11-22", 600, "yt" to 600), today, ReportRange.DAYS)
        assertEquals(7, periods.size)
        assertEquals(LocalDate.of(2026, 11, 16), periods.first().start)
        assertEquals("Today", periods.last().label)
        assertEquals(600L, periods.last().tvSeconds)
        assertEquals(3_600L, periods[5].tvSeconds)
        assertEquals(0, periods.first().daysWithData)
    }

    @Test
    fun `weeks start on Monday and add up each app across the week`() {
        val history = UsageHistory(
            listOf(
                day("2026-11-16", 1_000, "yt" to 800, "nf" to 200),
                day("2026-11-20", 2_000, "yt" to 500, "nf" to 1_500),
                day("2026-11-15", 9_999, "yt" to 9_999), // the Sunday before: last week
            ),
        )
        val periods = UsageReport.periods(history, day("2026-11-22", 0), today, ReportRange.WEEKS)
        val thisWeek = periods.last()
        assertEquals(LocalDate.of(2026, 11, 16), thisWeek.start)
        assertEquals("This week", thisWeek.title)
        assertEquals(3_000L, thisWeek.tvSeconds)
        assertEquals(listOf("nf" to 1_700L, "yt" to 1_300L), thisWeek.topApps)
        assertEquals(9_999L, periods[6].tvSeconds)
    }

    @Test
    fun `months are calendar months`() {
        val history = UsageHistory(listOf(day("2026-10-31", 100), day("2026-11-01", 200)))
        val periods = UsageReport.periods(history, day("2026-11-22", 50), today, ReportRange.MONTHS)
        assertEquals(6, periods.size)
        assertEquals(250L, periods.last().tvSeconds)
        assertEquals(100L, periods[4].tvSeconds)
        assertEquals("This month", periods.last().title)
    }

    @Test
    fun `history replaces a repeated date and keeps only the newest days`() {
        var history = UsageHistory()
        history = history.withDay(day("2026-11-20", 1)).withDay(day("2026-11-20", 2)).withDay(day("", 3))
        assertEquals(listOf(2L), history.days.map { it.tvSeconds })
        repeat(UsageHistory.KEEP_DAYS + 5) { history = history.withDay(day(today.minusDays(it.toLong()).toString(), 1)) }
        assertEquals(UsageHistory.KEEP_DAYS, history.days.size)
        assertEquals(today.toString(), history.days.last().date)
    }

    @Test
    fun `chart colors go to the apps used most across the whole range`() {
        val history = UsageHistory(listOf(day("2026-11-20", 500, "a" to 100, "b" to 400), day("2026-11-21", 300, "a" to 250, "c" to 50)))
        val periods = UsageReport.periods(history, day("2026-11-22", 0), today, ReportRange.DAYS)
        assertEquals(listOf("b", "a"), UsageReport.topPackages(periods, 2))
    }

    @Test
    fun `segments add up to the TV time, with the rest as other`() {
        val period = UsageReport.periods(UsageHistory(), day("2026-11-22", 1_000, "a" to 600, "b" to 300, "c" to 50), today, ReportRange.DAYS).last()
        assertEquals(listOf(600L, 300L, 100L), UsageReport.segments(period, listOf("a", "b")))
        val over = period.copy(tvSeconds = 450)
        val scaled = UsageReport.segments(over, listOf("a", "b"))
        assertEquals(450L, scaled.sum())
        assertEquals(listOf(300L, 150L, 0L), scaled)
    }
}
