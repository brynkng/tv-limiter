package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.GrantChoice
import dev.tvlimiter.domain.Profiles
import dev.tvlimiter.domain.UsageLedger
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The only in-memory copy of one profile's usage today. The tracking service charges time and the block
 * screen records grants through this one instance, so neither can overwrite the other.
 */
class UsageRepository(
    private val store: JsonFileStore<DayUsage>,
    private val clock: Clock,
    /** Receives each finished day when a new one starts, for the report. */
    private val archive: (DayUsage) -> Unit = {},
) {
    private val lock = Any()
    private var cached: DayUsage? = null

    fun today(): DayUsage = synchronized(lock) { current() }

    fun weekday(): DayOfWeek = LocalDate.now(clock).dayOfWeek

    fun charge(packageName: String, seconds: Long) = synchronized(lock) {
        save(UsageLedger.charge(current(), packageName, seconds, LocalDate.now(clock)))
    }

    fun grant(limitId: String, choice: GrantChoice) = synchronized(lock) {
        save(UsageLedger.grant(current(), limitId, choice, LocalDate.now(clock)))
    }

    fun clearGrants(limitId: String) = synchronized(lock) {
        save(UsageLedger.clearGrants(current(), limitId, LocalDate.now(clock)))
    }

    private fun current(): DayUsage {
        val loaded = cached ?: store.read()
        val rolled = UsageLedger.rollover(loaded, LocalDate.now(clock))
        if (rolled !== loaded) {
            if (loaded.date.isNotEmpty()) archive(loaded)
            save(rolled)
        } else {
            cached = rolled
        }
        return rolled
    }

    // The cache is updated first and the file write is best effort, so a full disk can't stop
    // time from counting or the block check from running. The next save writes it again.
    private fun save(usage: DayUsage) {
        cached = usage
        try {
            store.write(usage)
        } catch (e: IOException) {
            // Kept in memory; retried on the next save.
        }
    }

    companion object {
        private val instances = mutableMapOf<String, UsageRepository>()

        /** Today's usage for [profileId]; the shared profile is today's usage.json. */
        fun get(context: Context, profileId: String = Profiles.DEFAULT_ID): UsageRepository = synchronized(instances) {
            instances.getOrPut(profileId) {
                val file = Profiles.dataFile(context.applicationContext.filesDir, profileId, "usage.json")
                UsageRepository(
                    JsonFileStore(file, DayUsage.serializer()) { DayUsage(date = "") },
                    Clock.systemDefaultZone(),
                    HistoryRepository.get(context, profileId)::archive,
                )
            }
        }

        fun forget(profileId: String) = synchronized(instances) { instances.remove(profileId) }
    }
}
