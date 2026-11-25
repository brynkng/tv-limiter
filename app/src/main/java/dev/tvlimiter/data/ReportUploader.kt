package dev.tvlimiter.data

import dev.tvlimiter.domain.Backoff
import dev.tvlimiter.domain.Moment
import dev.tvlimiter.domain.OutcomeWire
import dev.tvlimiter.domain.QueuedReport
import dev.tvlimiter.domain.TubeGuardWire
import dev.tvlimiter.domain.UploadOutcome
import dev.tvlimiter.domain.WatchOutcome
import java.io.IOException

sealed interface FlushResult {
    /** The queue is empty. */
    data object Done : FlushResult

    /** Something is still queued. Try again after [afterMs]. */
    data class Retry(val afterMs: Long) : FlushResult

    /** The backend no longer knows this TV. */
    data object Disconnected : FlushResult
}

/**
 * Sends the queued reports in order, oldest first, stopping at the first one that fails so order
 * is kept. The id the backend gives a report is kept, so a later correction can name it. A failure starts a growing wait, and nothing is sent until it is over.
 */
class ReportUploader(
    private val queue: ReportQueueRepository,
    private val transport: TubeGuardTransport,
    private val token: () -> String?,
    private val now: () -> Moment,
    private val bootCount: () -> Int,
    private val backoff: Backoff = Backoff(),
    /** Told of each report the backend took, with its id and the outcome the reply carried (null while not decided). */
    private val onSent: (QueuedReport, String?, WatchOutcome?) -> Unit = { _, _, _ -> },
    /** Told of the Shorts setting a reply carried (true for Block), for the children the report counted for. */
    private val onShortsSetting: (QueuedReport, Boolean) -> Unit = { _, _ -> },
) {
    fun flush(): FlushResult {
        val deviceToken = token() ?: return FlushResult.Done
        while (true) {
            val moment = now()
            val wait = backoff.waitLeftMs(moment.elapsedMs)
            if (wait > 0) return FlushResult.Retry(wait)
            val report = queue.first() ?: return FlushResult.Done
            var replaces: String? = null
            if (report.correction) {
                replaces = queue.sentId(report)
                if (replaces == null) {
                    // The watch it corrects is no longer known, and sending it alone would add a watch.
                    queue.removeFirst(report)
                    continue
                }
            }
            val reply = try {
                transport.post(TubeGuardWire.REPORTS, deviceToken, TubeGuardWire.reportBody(report, moment, bootCount(), replaces))
            } catch (e: IOException) {
                return FlushResult.Retry(backoff.failure(moment.elapsedMs))
            }
            when (UploadOutcome.of(reply.status)) {
                UploadOutcome.Sent -> {
                    backoff.success()
                    val watchEventId = TubeGuardWire.watchEventId(reply.body)
                    if (report.correction) queue.removeFirst(report) else queue.markSent(report, watchEventId)
                    TubeGuardWire.shortsBlocked(reply.body)?.let { onShortsSetting(report, it) }
                    onSent(report, watchEventId ?: replaces, OutcomeWire.parse(reply.body))
                }
                UploadOutcome.Rejected -> {
                    backoff.success()
                    queue.removeFirst(report)
                }
                UploadOutcome.Retry -> return FlushResult.Retry(backoff.failure(moment.elapsedMs))
                UploadOutcome.Disconnected -> return FlushResult.Disconnected
            }
        }
    }
}
