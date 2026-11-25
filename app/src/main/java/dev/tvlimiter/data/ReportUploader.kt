package dev.tvlimiter.data

import dev.tvlimiter.domain.Backoff
import dev.tvlimiter.domain.Moment
import dev.tvlimiter.domain.TubeGuardWire
import dev.tvlimiter.domain.UploadOutcome
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
 * is kept. A failure starts a growing wait, and nothing is sent until it is over.
 */
class ReportUploader(
    private val queue: ReportQueueRepository,
    private val transport: TubeGuardTransport,
    private val token: () -> String?,
    private val now: () -> Moment,
    private val bootCount: () -> Int,
    private val backoff: Backoff = Backoff(),
) {
    fun flush(): FlushResult {
        val deviceToken = token() ?: return FlushResult.Done
        while (true) {
            val moment = now()
            val wait = backoff.waitLeftMs(moment.elapsedMs)
            if (wait > 0) return FlushResult.Retry(wait)
            val report = queue.first() ?: return FlushResult.Done
            val status = try {
                transport.post(TubeGuardWire.REPORTS, deviceToken, TubeGuardWire.reportBody(report, moment, bootCount())).status
            } catch (e: IOException) {
                return FlushResult.Retry(backoff.failure(moment.elapsedMs))
            }
            when (UploadOutcome.of(status)) {
                UploadOutcome.Sent, UploadOutcome.Rejected -> {
                    backoff.success()
                    queue.removeFirst(report)
                }
                UploadOutcome.Retry -> return FlushResult.Retry(backoff.failure(moment.elapsedMs))
                UploadOutcome.Disconnected -> return FlushResult.Disconnected
            }
        }
    }
}
