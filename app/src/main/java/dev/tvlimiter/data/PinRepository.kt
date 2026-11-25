package dev.tvlimiter.data

import android.content.Context
import android.os.SystemClock
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.PinHasher
import dev.tvlimiter.domain.PinRecord
import dev.tvlimiter.domain.PinStore
import dev.tvlimiter.domain.Profiles
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable

@Serializable
data class PinFile(val record: PinRecord? = null)

/** One passkey record: the parent passkey (pin.json) or one child's. */
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

    override fun clear() = synchronized(lock) {
        val file = PinFile()
        cached = file
        try {
            store.write(file)
        } catch (e: IOException) {
            // Kept in memory; retried on the next save.
        }
    }

    companion object {
        private val guards = mutableMapOf<String, PinGuard>()

        /**
         * The passkey check for [profileId], or the parent passkey (pin.json) when it is null.
         * Each profile's passkey is a separate file, see [Profiles.passkeyFile].
         */
        fun guard(context: Context, profileId: String? = null): PinGuard = synchronized(guards) {
            guards.getOrPut(profileId ?: PARENT) {
                val dir = context.applicationContext.filesDir
                val file = if (profileId == null) File(dir, "pin.json") else Profiles.passkeyFile(dir, profileId)
                PinGuard(
                    PinRepository(JsonFileStore(file, PinFile.serializer()) { PinFile() }),
                    PinHasher(),
                    SystemClock::elapsedRealtime,
                )
            }
        }

        private const val PARENT = "parent"

        fun forget(profileId: String) = synchronized(guards) { guards.remove(profileId) }
    }
}
