package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.QueuedReport
import dev.tvlimiter.domain.SentReport
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class ReportQueueFile(val reports: List<QueuedReport> = emptyList(), val sent: List<SentReport> = emptyList())

/**
 * Reports waiting for upload, oldest first, in tubeguard-reports.json. A report stays here until the backend
 * has taken it, so a lost connection or a restart loses nothing. Past [MAX_REPORTS] the oldest go, so a TV that
 * is offline for weeks can't fill its storage.
 *
 * The file also remembers the id the backend gave each of the last few reports it took, so a correction (the
 * same watch with a longer length, because the first length was an ad's) can name the watch it changes.
 */
class ReportQueueRepository(private val store: JsonFileStore<ReportQueueFile>) {
    private val lock = Any()
    private var cached: ReportQueueFile? = null

    private fun load(): ReportQueueFile = cached ?: store.read().also { cached = it }

    fun all(): List<QueuedReport> = synchronized(lock) { load().reports }

    fun first(): QueuedReport? = synchronized(lock) { load().reports.firstOrNull() }

    /** The backend's id for the watch [report] belongs to, once that watch has been sent. */
    fun sentId(report: QueuedReport): String? = synchronized(lock) { load().sent.lastOrNull { it.key == report.key }?.watchEventId }

    /**
     * Queues [report]. A correction does not add a watch: while the original is still waiting it just changes the
     * original's length, a correction already waiting takes the newer length, and one for a watch the backend has
     * taken is queued. A correction for a watch this queue doesn't know is dropped.
     */
    fun add(report: QueuedReport) = synchronized(lock) {
        val file = load()
        if (!report.correction) return@synchronized save(file.copy(reports = (file.reports + report).takeLast(MAX_REPORTS)))
        val waiting = file.reports.indexOfFirst { it.key == report.key }
        when {
            waiting >= 0 -> save(file.copy(reports = file.reports.mapIndexed { i, r -> if (i == waiting) r.copy(durationSec = report.durationSec) else r }))
            file.sent.any { it.key == report.key } -> save(file.copy(reports = (file.reports + report).takeLast(MAX_REPORTS)))
        }
    }

    /**
     * The backend took [report] and named it [watchEventId]. It is removed from the queue and its id remembered.
     * If its length was corrected while it was being sent, the correction is queued in its place.
     */
    fun markSent(report: QueuedReport, watchEventId: String?) = synchronized(lock) {
        val file = load()
        val index = file.reports.indexOfFirst { it.key == report.key && !it.correction }
        if (index < 0) return@synchronized
        val now = file.reports[index]
        val kept = file.reports.toMutableList()
        if (now.durationSec != report.durationSec) kept[index] = now.copy(correction = true) else kept.removeAt(index)
        val sent = if (watchEventId == null) file.sent else (file.sent + SentReport(report.key, watchEventId)).takeLast(MAX_SENT)
        save(ReportQueueFile(kept, sent))
    }

    /** Removes [report] if it is still first, so a send that raced with a clear can't remove a newer one. */
    fun removeFirst(report: QueuedReport) = synchronized(lock) {
        val file = load()
        if (file.reports.firstOrNull() == report) save(file.copy(reports = file.reports.drop(1)))
    }

    fun clear() = synchronized(lock) { save(ReportQueueFile()) }

    private fun save(file: ReportQueueFile) {
        store.write(file)
        cached = file
    }

    companion object {
        const val MAX_REPORTS = 500

        /** Corrections come within minutes of a report, so only the last few ids are worth keeping. */
        const val MAX_SENT = 20

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
