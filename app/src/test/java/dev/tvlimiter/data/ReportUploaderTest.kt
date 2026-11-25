package dev.tvlimiter.data

import dev.tvlimiter.domain.LinkStatus
import dev.tvlimiter.domain.Moment
import dev.tvlimiter.domain.QueuedReport
import dev.tvlimiter.domain.TubeGuardLink
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportUploaderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun queue() = ReportQueueRepository(
        JsonFileStore(File(folder.root, "tubeguard-reports.json"), ReportQueueFile.serializer()) { ReportQueueFile() },
    )

    private fun report(title: String, boot: Int = 7, elapsed: Long = 0, wall: Long = 0) =
        QueuedReport(title, "Channel", 100, startElapsedMs = elapsed, startWallMs = wall, bootCount = boot)

    private class FakeTransport(var replies: MutableList<Any> = mutableListOf()) : TubeGuardTransport {
        val sent = mutableListOf<Triple<String, String?, String>>()
        override fun post(path: String, token: String?, body: String): HttpReply {
            sent += Triple(path, token, body)
            val next = if (replies.isEmpty()) HttpReply(201, "{}") else replies.removeAt(0)
            if (next is IOException) throw next
            return next as HttpReply
        }
        fun titles() = sent.map { Json.parseToJsonElement(it.third).jsonObject["title"]!!.jsonPrimitive.content }
    }

    private var clock = Moment(100_000, 9_000_000)
    private var boot = 7
    private fun uploader(queue: ReportQueueRepository, transport: TubeGuardTransport, token: String? = "tok") =
        ReportUploader(queue, transport, token = { token }, now = { clock }, bootCount = { boot })

    @Test
    fun `reports are sent oldest first with the device token`() {
        val queue = queue()
        listOf("one", "two", "three").forEach { queue.add(report(it)) }
        val transport = FakeTransport()
        assertEquals(FlushResult.Done, uploader(queue, transport).flush())
        assertEquals(listOf("one", "two", "three"), transport.titles())
        assertTrue(transport.sent.all { it.first == "/api/reports" && it.second == "tok" })
        assertTrue(queue.all().isEmpty())
    }

    @Test
    fun `a failed report stays first and the ones behind it wait`() {
        val queue = queue()
        listOf("one", "two").forEach { queue.add(report(it)) }
        val transport = FakeTransport(mutableListOf(IOException("offline")))
        val result = uploader(queue, transport).flush()
        assertEquals(FlushResult.Retry(30_000), result)
        assertEquals(listOf("one"), transport.titles())
        assertEquals(listOf("one", "two"), queue.all().map { it.title })
    }

    @Test
    fun `nothing is sent until the wait is over, then it goes in order`() {
        val queue = queue()
        listOf("one", "two").forEach { queue.add(report(it)) }
        val transport = FakeTransport(mutableListOf(HttpReply(503, "")))
        val uploader = uploader(queue, transport)
        uploader.flush()
        clock = Moment(110_000, 9_010_000)
        assertEquals(FlushResult.Retry(20_000), uploader.flush())
        assertEquals("no second attempt during the wait", 1, transport.sent.size)
        clock = Moment(131_000, 9_031_000)
        assertEquals(FlushResult.Done, uploader.flush())
        assertEquals(listOf("one", "one", "two"), transport.titles())
    }

    @Test
    fun `the wait grows with each failure and a success resets it`() {
        val queue = queue()
        queue.add(report("one"))
        val transport = FakeTransport(mutableListOf(IOException(), IOException(), HttpReply(201, "")))
        val uploader = uploader(queue, transport)
        assertEquals(FlushResult.Retry(30_000), uploader.flush())
        clock = Moment(clock.elapsedMs + 30_000, clock.wallMs + 30_000)
        assertEquals(FlushResult.Retry(60_000), uploader.flush())
        clock = Moment(clock.elapsedMs + 60_000, clock.wallMs + 60_000)
        assertEquals(FlushResult.Done, uploader.flush())
        queue.add(report("two"))
        transport.replies += IOException()
        assertEquals(FlushResult.Retry(30_000), uploader.flush())
    }

    @Test
    fun `a rate limit keeps the report for later`() {
        val queue = queue().apply { add(report("one")) }
        val result = uploader(queue, FakeTransport(mutableListOf(HttpReply(429, "")))).flush()
        assertTrue(result is FlushResult.Retry)
        assertEquals(1, queue.all().size)
    }

    @Test
    fun `a report the backend refuses is dropped so it cannot block the rest`() {
        val queue = queue()
        listOf("bad", "good").forEach { queue.add(report(it)) }
        val transport = FakeTransport(mutableListOf(HttpReply(422, "")))
        assertEquals(FlushResult.Done, uploader(queue, transport).flush())
        assertEquals(listOf("bad", "good"), transport.titles())
        assertTrue(queue.all().isEmpty())
    }

    @Test
    fun `a 401 stops everything and keeps nothing sent`() {
        val queue = queue()
        listOf("one", "two").forEach { queue.add(report(it)) }
        val transport = FakeTransport(mutableListOf(HttpReply(401, """{"error":"unauthorized"}""")))
        assertEquals(FlushResult.Disconnected, uploader(queue, transport).flush())
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `without a token nothing is sent`() {
        val queue = queue().apply { add(report("one")) }
        val transport = FakeTransport()
        assertEquals(FlushResult.Done, uploader(queue, transport, token = null).flush())
        assertTrue(transport.sent.isEmpty())
        assertEquals(1, queue.all().size)
    }

    @Test
    fun `a report queued an hour ago is sent with an age of about an hour`() {
        val queue = queue().apply { add(report("one", elapsed = 100_000)) }
        clock = Moment(100_000 + 3_600_000, 0)
        val transport = FakeTransport()
        uploader(queue, transport).flush()
        val body = Json.parseToJsonElement(transport.sent.single().third).jsonObject
        assertEquals(3_600_000, body["ageMs"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `a report queued before a reboot is sent with its old boot count and a wall clock age`() {
        val first = queue().apply { add(report("old", boot = 7, elapsed = 5_000_000, wall = 8_000_000)) }
        // The TV restarted: a new queue object reads the file, elapsed time began again, and the boot count went up.
        val reread = queue()
        assertEquals(first.all(), reread.all())
        boot = 8
        clock = Moment(60_000, 8_000_000 + 2 * 3_600_000)
        val transport = FakeTransport()
        uploader(reread, transport).flush()
        val body = Json.parseToJsonElement(transport.sent.single().third).jsonObject
        assertEquals(7, body["bootCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(8, body["currentBootCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(2 * 3_600_000L, body["ageMs"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `the queue survives a restart in order`() {
        queue().apply { listOf("a", "b", "c").forEach { add(report(it)) } }
        assertEquals(listOf("a", "b", "c"), queue().all().map { it.title })
    }

    @Test
    fun `the queue keeps only the newest 500`() {
        val queue = queue()
        repeat(ReportQueueRepository.MAX_REPORTS + 5) { queue.add(report("r$it")) }
        assertEquals(ReportQueueRepository.MAX_REPORTS, queue.all().size)
        assertEquals("r5", queue.first()!!.title)
    }

    @Test
    fun `removing the first report leaves a newer one alone`() {
        val queue = queue()
        val a = report("a")
        queue.add(a)
        queue.clear()
        queue.add(report("b"))
        queue.removeFirst(a)
        assertEquals(listOf("b"), queue.all().map { it.title })
    }

    @Test
    fun `an empty queue has no first report`() {
        assertNull(queue().first())
    }

    @Test
    fun `the link keeps the token, and a disconnect drops it`() {
        val repo = { TubeGuardLinkRepository(JsonFileStore(File(folder.root, "tubeguard.json"), TubeGuardLink.serializer()) { TubeGuardLink() }) }
        repo().connect("d1", "tok", listOf("Ana"))
        assertTrue(repo().current().isConnected)
        assertEquals("tok", repo().current().deviceToken)

        repo().markDisconnected()
        val after = repo().current()
        assertEquals(LinkStatus.DISCONNECTED, after.status)
        assertNull(after.deviceToken)
        assertEquals(listOf("Ana"), after.children)

        repo().forget()
        assertEquals(LinkStatus.NOT_CONNECTED, repo().current().status)
    }
}
