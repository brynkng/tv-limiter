package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.QueuedReport
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class ReportQueueFile(val reports: List<QueuedReport> = emptyList())

/**
 * Reports waiting for upload, oldest first, in tubeguard-reports.json. A report stays here until the backend
 * has taken it, so a lost connection or a restart loses nothing. Past [MAX_REPORTS] the oldest go, so a TV that
 * is offline for weeks can't fill its storage.
 */
class ReportQueueRepository(private val store: JsonFileStore<ReportQueueFile>) {
    private val lock = Any()
    private var cached: List<QueuedReport>? = null

    private fun load(): List<QueuedReport> = cached ?: store.read().reports.also { cached = it }

    fun all(): List<QueuedReport> = synchronized(lock) { load() }

    fun first(): QueuedReport? = synchronized(lock) { load().firstOrNull() }

    fun add(report: QueuedReport) = synchronized(lock) { save((load() + report).takeLast(MAX_REPORTS)) }

    /** Removes [report] if it is still first, so a send that raced with a clear can't remove a newer one. */
    fun removeFirst(report: QueuedReport) = synchronized(lock) {
        if (load().firstOrNull() == report) save(load().drop(1))
    }

    fun clear() = synchronized(lock) { save(emptyList()) }

    private fun save(reports: List<QueuedReport>) {
        store.write(ReportQueueFile(reports))
        cached = reports
    }

    companion object {
        const val MAX_REPORTS = 500

        @Volatile private var instance: ReportQueueRepository? = null

        fun get(context: Context): ReportQueueRepository = instance ?: synchronized(this) {
            instance ?: ReportQueueRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "tubeguard-reports.json"), ReportQueueFile.serializer()) {
                    ReportQueueFile()
                },
            ).also { instance = it }
        }
    }
}
