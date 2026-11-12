package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.Limit
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class LimitsFile(val limits: List<Limit> = listOf(Limit.defaultWholeTv()))

/**
 * The only in-memory copy of the limits. The service, the block screen and the settings
 * screens all go through this one instance, so an edit is never overwritten by a stale copy.
 */
class LimitsRepository(private val store: JsonFileStore<LimitsFile>) {
    private val lock = Any()
    private var cached: List<Limit>? = null

    fun all(): List<Limit> = synchronized(lock) { load() }

    fun find(id: String): Limit? = all().firstOrNull { it.id == id }

    fun upsert(limit: Limit) = synchronized(lock) {
        val current = load()
        val next = if (current.any { it.id == limit.id }) {
            current.map { if (it.id == limit.id) limit else it }
        } else {
            current + limit
        }
        save(next)
    }

    fun delete(id: String) = synchronized(lock) {
        if (id == Limit.WHOLE_TV_ID) return@synchronized
        save(load().filterNot { it.id == id })
    }

    private fun load(): List<Limit> {
        cached?.let { return it }
        val limits = store.read().limits.let { stored ->
            if (stored.any { it.isWholeTv }) stored else listOf(Limit.defaultWholeTv()) + stored
        }
        cached = limits
        return limits
    }

    private fun save(limits: List<Limit>) {
        store.write(LimitsFile(limits))
        cached = limits
    }

    companion object {
        @Volatile private var instance: LimitsRepository? = null

        fun get(context: Context): LimitsRepository = instance ?: synchronized(this) {
            instance ?: LimitsRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "limits.json"), LimitsFile.serializer()) {
                    LimitsFile()
                },
            ).also { instance = it }
        }
    }
}
