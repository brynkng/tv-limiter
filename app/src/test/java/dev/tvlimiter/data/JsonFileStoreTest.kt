package dev.tvlimiter.data

import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.Limit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonFileStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private fun limitsStore(file: File) = JsonFileStore(file, LimitsFile.serializer()) { LimitsFile() }

    @Test
    fun `usage written and read back is identical`() {
        val store = JsonFileStore(File(temp.root, "usage.json"), DayUsage.serializer()) { DayUsage(date = "") }
        val usage = DayUsage(
            date = "2026-11-10",
            secondsByPackage = mapOf("a" to 12),
            tvSeconds = 40,
            grantMinutesByLimitId = mapOf("app:a" to 15),
            restOfDayLimitIds = setOf("tv"),
        )
        store.write(usage)
        assertEquals(usage, store.read())
        assertFalse(File(temp.root, "usage.json.tmp").exists())
    }

    @Test
    fun `limits round trip including no limit days`() {
        val store = limitsStore(File(temp.root, "limits.json"))
        val app = Limit.newAppLimit("a").copy(minutesByDay = listOf(30, null, 45, null, 60, null, 0))
        val file = LimitsFile(listOf(Limit.defaultWholeTv(), app))
        store.write(file)
        assertEquals(file, store.read())
    }

    @Test
    fun `a missing file gives the defaults`() {
        val defaults = limitsStore(File(temp.root, "none.json")).read()
        assertEquals(listOf(Limit.defaultWholeTv()), defaults.limits)
        assertFalse(defaults.limits.single().enabled)
    }

    @Test
    fun `a corrupt file gives the defaults and is kept aside`() {
        val file = File(temp.root, "limits.json").apply { writeText("{not json") }
        val store = limitsStore(file)
        val read = store.read()
        assertEquals(LimitsFile(), read)
        assertTrue(File(temp.root, "limits.json.corrupt").exists())
        assertTrue(store.hasCorruptCopy())
        assertFalse(file.exists())
    }
}
