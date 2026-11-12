package dev.tvlimiter.data

import android.content.Context
import android.os.SystemClock
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.PinHasher
import dev.tvlimiter.domain.PinRecord
import dev.tvlimiter.domain.PinStore
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable

@Serializable
data class PinFile(val record: PinRecord? = null)

/** The one PIN record shared by every PIN prompt in the app. */
class PinRepository(private val store: JsonFileStore<PinFile>) : PinStore {
    private val lock = Any()
    private var cached: PinFile? = null

    override fun load(): PinRecord? = synchronized(lock) {
        (cached ?: store.read().also { cached = it }).record
    }

    override fun isDamaged(): Boolean = synchronized(lock) { store.hasCorruptCopy() }

    override fun save(record: PinRecord) = synchronized(lock) {
        // Cache first so a failed write still counts a wrong PIN for this run, rather than
        // crashing the PIN prompt.
        val file = PinFile(record)
        cached = file
        try {
            store.write(file)
        } catch (e: IOException) {
            // Kept in memory; retried on the next save.
        }
    }

    companion object {
        @Volatile private var guard: PinGuard? = null

        fun guard(context: Context): PinGuard = guard ?: synchronized(this) {
            guard ?: PinGuard(
                PinRepository(
                    JsonFileStore(File(context.applicationContext.filesDir, "pin.json"), PinFile.serializer()) {
                        PinFile()
                    },
                ),
                PinHasher(),
                SystemClock::elapsedRealtime,
            ).also { guard = it }
        }
    }
}
