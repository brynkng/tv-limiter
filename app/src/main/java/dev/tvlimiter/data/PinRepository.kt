package dev.tvlimiter.data

import android.content.Context
import android.os.SystemClock
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.PinHasher
import dev.tvlimiter.domain.PinRecord
import dev.tvlimiter.domain.PinStore
import java.io.File
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

    override fun save(record: PinRecord) = synchronized(lock) {
        val file = PinFile(record)
        store.write(file)
        cached = file
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
