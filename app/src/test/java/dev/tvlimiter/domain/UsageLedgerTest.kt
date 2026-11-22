package dev.tvlimiter.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLedgerTest {
    private val day = LocalDate.parse("2026-11-10")

    @Test
    fun `charging a package also charges the TV total`() {
        val usage = UsageLedger.charge(DayUsage(date = day.toString()), "a", 30, day)
        assertEquals(30L, usage.secondsFor("a"))
        assertEquals(30L, usage.tvSeconds)
    }

    @Test
    fun `the first charge after midnight starts the new day from zero without old grants`() {
        val yesterday = DayUsage(
            date = day.toString(),
            secondsByPackage = mapOf("a" to 3000),
            tvSeconds = 3000,
            grantMinutesByLimitId = mapOf("app:a" to 30),
            restOfDayLimitIds = setOf("tv"),
        )
        val today = UsageLedger.charge(yesterday, "a", 2, day.plusDays(1))
        assertEquals(day.plusDays(1).toString(), today.date)
        assertEquals(2L, today.secondsFor("a"))
        assertEquals(2L, today.tvSeconds)
        assertTrue(today.grantMinutesByLimitId.isEmpty())
        assertTrue(today.restOfDayLimitIds.isEmpty())
    }

    @Test
    fun `grants of minutes add up`() {
        var usage = DayUsage(date = day.toString())
        usage = UsageLedger.grant(usage, "app:a", GrantChoice.PLUS_15, day)
        usage = UsageLedger.grant(usage, "app:a", GrantChoice.PLUS_30, day)
        assertEquals(45, usage.grantMinutesByLimitId["app:a"])
    }

    @Test
    fun `clearing grants takes back added minutes and rest of today for that limit only`() {
        var usage = DayUsage(date = day.toString())
        usage = UsageLedger.grant(usage, "app:a", GrantChoice.PLUS_30, day)
        usage = UsageLedger.grant(usage, "app:a", GrantChoice.REST_OF_DAY, day)
        usage = UsageLedger.grant(usage, "tv", GrantChoice.PLUS_15, day)
        assertEquals("the rest of today", usage.addedTime("app:a"))

        usage = UsageLedger.clearGrants(usage, "app:a", day)

        assertEquals(null, usage.addedTime("app:a"))
        assertTrue("app:a" !in usage.restOfDayLimitIds)
        assertEquals("+15 min", usage.addedTime("tv"))
    }
}
