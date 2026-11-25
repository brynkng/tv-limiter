package dev.tvlimiter.data

import dev.tvlimiter.domain.Span
import dev.tvlimiter.domain.TubeGuardWire
import dev.tvlimiter.domain.UploadOutcome
import java.io.IOException

/**
 * Tells TubeGuard what part of each watch was played. Every update carries all the spans of its watch, so only the
 * newest one for a watch is kept, and one that could not be sent is made up by the next. An update for a watch whose
 * report has not been taken yet waits for the report's id. Nothing here backs off: updates come every half minute
 * while a child watches, and a failed one is tried again with the next.
 */
class SpanUploader(
    private val queue: ReportQueueRepository,
    private val transport: TubeGuardTransport,
    private val token: () -> String?,
) {
    private val lock = Any()
    private val pending = LinkedHashMap<String, Pair<List<Span>, Int?>>()

    /** Keeps the newest spans for the watch named [key], to send with the next [flush]. */
    fun add(key: String, spans: List<Span>, position: Int? = null) = synchronized(lock) {
        pending.remove(key)
        pending[key] = spans to position
        while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
    }

    /** Sends what is waiting and can be sent. Returns true when nothing is left waiting for a retry. */
    fun flush(): Boolean {
        val deviceToken = token() ?: return true
        val due = synchronized(lock) { pending.entries.map { it.key to it.value } }
        for ((key, entry) in due) {
            val (spans, position) = entry
            val id = queue.sentId(key) ?: continue
            val outcome = try {
                UploadOutcome.of(transport.post(TubeGuardWire.spansPath(id), deviceToken, TubeGuardWire.spansBody(spans, position)).status)
            } catch (e: IOException) {
                UploadOutcome.Retry
            }
            when (outcome) {
                // A newer list that arrived while this one was going stays.
                UploadOutcome.Sent, UploadOutcome.Rejected -> synchronized(lock) { if (pending[key] == entry) pending.remove(key) }
                UploadOutcome.Retry -> return false
                UploadOutcome.Disconnected -> {
                    synchronized(lock) { pending.clear() }
                    return true
                }
            }
        }
        return synchronized(lock) { pending.isEmpty() }
    }

    fun clear() = synchronized(lock) { pending.clear() }

    private companion object {
        /** More than the watches that can be open at once. */
        const val MAX_PENDING = 20
    }
}
