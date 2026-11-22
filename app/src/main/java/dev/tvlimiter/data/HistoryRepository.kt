package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.UsageHistory
import java.io.File
import java.io.IOException

/** Finished days of usage, kept for the report. Written once per day, when a new day starts. */
class HistoryRepository(private val store: JsonFileStore<UsageHistory>) {
    private val lock = Any()
    private var cached: UsageHistory? = null

    fun all(): UsageHistory = synchronized(lock) { cached ?: store.read().also { cached = it } }

    fun archive(day: DayUsage) = synchronized(lock) {
        val updated = all().withDay(day)
        cached = updated
        try {
            store.write(updated)
        } catch (e: IOException) {
            // Kept in memory; written again with the next finished day.
        }
    }

    companion object {
        @Volatile private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository = instance ?: synchronized(this) {
            instance ?: HistoryRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "history.json"), UsageHistory.serializer()) { UsageHistory() },
            ).also { instance = it }
        }
    }
}
