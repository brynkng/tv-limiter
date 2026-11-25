package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.Profiles
import dev.tvlimiter.domain.UsageHistory
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
        private val instances = mutableMapOf<String, HistoryRepository>()

        /** The history of [profileId]; the shared profile is today's history.json. */
        fun get(context: Context, profileId: String = Profiles.DEFAULT_ID): HistoryRepository = synchronized(instances) {
            instances.getOrPut(profileId) {
                val file = Profiles.dataFile(context.applicationContext.filesDir, profileId, "history.json")
                HistoryRepository(JsonFileStore(file, UsageHistory.serializer()) { UsageHistory() })
            }
        }

        fun forget(profileId: String) = synchronized(instances) { instances.remove(profileId) }
    }
}
