package dev.tvlimiter.data

import dev.tvlimiter.domain.DayUsage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class UsageRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private class MovableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    @Test
    fun `the first use after midnight archives the finished day once`() {
        val clock = MovableClock(Instant.parse("2026-11-21T20:00:00Z"))
        val archived = mutableListOf<DayUsage>()
        val store = JsonFileStore(folder.newFile("usage.json").also { it.delete() }, DayUsage.serializer()) { DayUsage(date = "") }
        val repo = UsageRepository(store, clock) { archived += it }

        repo.charge("yt", 120)
        assertEquals(emptyList<DayUsage>(), archived)

        clock.instant = Instant.parse("2026-11-22T08:00:00Z")
        repo.charge("yt", 30)
        repo.today()

        assertEquals(listOf("2026-11-21"), archived.map { it.date })
        assertEquals(120L, archived.single().tvSeconds)
        assertEquals(30L, repo.today().tvSeconds)
    }
}
