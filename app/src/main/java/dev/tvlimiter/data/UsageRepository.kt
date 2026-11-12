package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.GrantChoice
import dev.tvlimiter.domain.UsageLedger
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The only in-memory copy of today's usage. The tracking service charges time and the block
 * screen records grants through this one instance, so neither can overwrite the other.
 */
class UsageRepository(
    private val store: JsonFileStore<DayUsage>,
    private val clock: Clock,
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

    private fun current(): DayUsage {
        val loaded = cached ?: store.read()
        val rolled = UsageLedger.rollover(loaded, LocalDate.now(clock))
        if (rolled !== loaded) save(rolled) else cached = rolled
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
        @Volatile private var instance: UsageRepository? = null

        fun get(context: Context): UsageRepository = instance ?: synchronized(this) {
            instance ?: UsageRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "usage.json"), DayUsage.serializer()) {
                    DayUsage(date = "")
                },
                Clock.systemDefaultZone(),
            ).also { instance = it }
        }
    }
}
