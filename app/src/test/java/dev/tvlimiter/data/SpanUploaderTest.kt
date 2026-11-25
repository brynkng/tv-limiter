package dev.tvlimiter.data

import dev.tvlimiter.domain.QueuedReport
import dev.tvlimiter.domain.Span
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpanUploaderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun queue() = ReportQueueRepository(
        JsonFileStore(File(folder.root, "tubeguard-reports.json"), ReportQueueFile.serializer()) { ReportQueueFile() },
    )

    private val report = QueuedReport("A title", "Channel", 3000, startElapsedMs = 10_000, startWallMs = 0, bootCount = 7)
    private val key = report.key

    private class FakeTransport(var replies: MutableList<Any> = mutableListOf()) : TubeGuardTransport {
        val sent = mutableListOf<Triple<String, String?, String>>()
        override fun post(path: String, token: String?, body: String): HttpReply {
            sent += Triple(path, token, body)
            val next = if (replies.isEmpty()) HttpReply(200, "{}") else replies.removeAt(0)
            if (next is IOException) throw next
            return next as HttpReply
        }
        override fun get(path: String, token: String?): HttpReply = throw UnsupportedOperationException()
    }

    private fun sentReport(queue: ReportQueueRepository, id: String = "w1") {
        queue.add(report)
        queue.markSent(report, id)
    }

    @Test
    fun `spans go to the watch's path with the device token, once its report has an id`() {
        val queue = queue()
        val transport = FakeTransport()
        val uploader = SpanUploader(queue, transport, token = { "tok" })
        uploader.add(key, listOf(Span(0, 70)))
        // The report has not been taken yet, so the spans wait.
        assertFalse(uploader.flush())
        assertTrue(transport.sent.isEmpty())

        sentReport(queue)
        assertTrue(uploader.flush())
        assertEquals(listOf(Triple("/api/device/watches/w1/spans", "tok" as String?, """{"spans":[[0,70]]}""")), transport.sent)
        // Sent once, so nothing more goes out.
        assertTrue(uploader.flush())
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `the position goes with the spans when there is one`() {
        val queue = queue()
        sentReport(queue)
        val transport = FakeTransport()
        val uploader = SpanUploader(queue, transport, token = { "tok" })
        uploader.add(key, listOf(Span(0, 70)), position = 12)
        uploader.flush()
        assertEquals("""{"spans":[[0,70]],"position":12}""", transport.sent.single().third)
    }

    @Test
    fun `only the newest list for a watch is kept, since each carries every span`() {
        val queue = queue()
        sentReport(queue)
        val transport = FakeTransport()
        val uploader = SpanUploader(queue, transport, token = { "tok" })
        uploader.add(key, listOf(Span(0, 30)))
        uploader.add(key, listOf(Span(0, 60)))
        uploader.flush()
        assertEquals(listOf("""{"spans":[[0,60]]}"""), transport.sent.map { it.third })
    }

    @Test
    fun `a failed send is kept and goes again, and a refused one is dropped`() {
        val queue = queue()
        sentReport(queue)
        val transport = FakeTransport(mutableListOf(IOException("offline"), HttpReply(503, ""), HttpReply(404, "")))
        val uploader = SpanUploader(queue, transport, token = { "tok" })
        uploader.add(key, listOf(Span(0, 70)))
        assertFalse(uploader.flush())
        assertFalse(uploader.flush())
        // The backend does not know the watch: nothing to keep.
        assertTrue(uploader.flush())
        assertEquals(3, transport.sent.size)
        assertTrue(uploader.flush())
        assertEquals(3, transport.sent.size)
    }

    @Test
    fun `a removed TV sends nothing more and forgets what was waiting`() {
        val queue = queue()
        sentReport(queue)
        val transport = FakeTransport(mutableListOf(HttpReply(401, "")))
        val uploader = SpanUploader(queue, transport, token = { "tok" })
        uploader.add(key, listOf(Span(0, 70)))
        assertTrue(uploader.flush())
        assertTrue(uploader.flush())
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `nothing is sent without a device token`() {
        val queue = queue()
        sentReport(queue)
        val transport = FakeTransport()
        val uploader = SpanUploader(queue, transport, token = { null })
        uploader.add(key, listOf(Span(0, 70)))
        assertTrue(uploader.flush())
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun `a waiting report that is corrected keeps the spans of the correction`() {
        val queue = queue()
        queue.add(report.copy(durationSec = 90, spans = listOf(Span(0, 5))))
        queue.add(report.copy(durationSec = 3000, correction = true, spans = listOf(Span(0, 18))))
        assertEquals(listOf(Span(0, 18)), queue.all().single().spans)
        assertEquals(3000, queue.all().single().durationSec)
    }
}
