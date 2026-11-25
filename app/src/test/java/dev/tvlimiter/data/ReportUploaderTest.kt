package dev.tvlimiter.data

import dev.tvlimiter.domain.LinkStatus
import dev.tvlimiter.domain.Moment
import dev.tvlimiter.domain.QueuedReport
import dev.tvlimiter.domain.TubeGuardChild
import dev.tvlimiter.domain.TubeGuardLink
import dev.tvlimiter.domain.WatchOutcome
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

    private fun report(title: String, boot: Int = 7, elapsed: Long = 0, wall: Long = 0, seconds: Int? = 100, correction: Boolean = false) =
        QueuedReport(title, "Channel", seconds, startElapsedMs = elapsed, startWallMs = wall, bootCount = boot, correction = correction)

    private fun body(transport: FakeTransport, index: Int = 0) = Json.parseToJsonElement(transport.sent[index].third).jsonObject

    private class FakeTransport(var replies: MutableList<Any> = mutableListOf()) : TubeGuardTransport {
        val sent = mutableListOf<Triple<String, String?, String>>()
        override fun post(path: String, token: String?, body: String): HttpReply {
            sent += Triple(path, token, body)
            val next = if (replies.isEmpty()) HttpReply(201, "{}") else replies.removeAt(0)
            if (next is IOException) throw next
            return next as HttpReply
        }
        override fun get(path: String, token: String?): HttpReply = throw UnsupportedOperationException("reports are only posted")
        fun titles() = sent.map { Json.parseToJsonElement(it.third).jsonObject["title"]!!.jsonPrimitive.content }
    }

    private var clock = Moment(100_000, 9_000_000)
    private var boot = 7
    private fun uploader(
        queue: ReportQueueRepository,
        transport: TubeGuardTransport,
        token: String? = "tok",
        onSent: (QueuedReport, String?, WatchOutcome?) -> Unit = { _, _, _ -> },
    ) = ReportUploader(queue, transport, token = { token }, now = { clock }, bootCount = { boot }, onSent = onSent)

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
    fun `the id the backend gives a report is kept, and survives a restart`() {
        val queue = queue().apply { add(report("one", elapsed = 5)) }
        val transport = FakeTransport(mutableListOf(HttpReply(201, """{"watchEventId":"w1","watchTime":1}""")))
        uploader(queue, transport).flush()
        assertTrue(queue.all().isEmpty())
        assertEquals("w1", queue.sentId(report("one", elapsed = 5)))
        assertEquals("w1", queue().sentId(report("one", elapsed = 5)))
        assertNull(queue.sentId(report("other", elapsed = 6)))
    }

    @Test
    fun `a correction after the report was sent names it and carries the new length`() {
        val queue = queue().apply { add(report("one", elapsed = 5, seconds = 120)) }
        val transport = FakeTransport(mutableListOf(HttpReply(201, """{"watchEventId":"w1"}"""), HttpReply(200, """{"watchEventId":"w1"}""")))
        val uploader = uploader(queue, transport)
        uploader.flush()
        queue.add(report("one", elapsed = 5, seconds = 900, correction = true))
        assertEquals(FlushResult.Done, uploader.flush())
        assertEquals(2, transport.sent.size)
        assertNull(body(transport, 0)["replaces"])
        assertEquals("w1", body(transport, 1)["replaces"]!!.jsonPrimitive.content)
        assertEquals(900, body(transport, 1)["durationSec"]!!.jsonPrimitive.content.toInt())
        assertTrue(queue.all().isEmpty())
    }

    @Test
    fun `a correction queued before the report was sent changes its length instead`() {
        val queue = queue()
        queue.add(report("one", elapsed = 5, seconds = 120))
        queue.add(report("one", elapsed = 5, seconds = 900, correction = true))
        assertEquals(listOf(900), queue.all().map { it.durationSec })
        assertTrue(queue.all().none { it.correction })
        val transport = FakeTransport(mutableListOf(HttpReply(201, """{"watchEventId":"w1"}""")))
        uploader(queue, transport).flush()
        assertEquals(1, transport.sent.size)
        assertEquals(900, body(transport)["durationSec"]!!.jsonPrimitive.content.toInt())
        assertNull(body(transport)["replaces"])
    }

    @Test
    fun `a correction that arrives while the report is being sent is queued once it is taken`() {
        val queue = queue()
        queue.add(report("one", elapsed = 5, seconds = 120))
        val inFlight = queue.first()!!
        queue.add(report("one", elapsed = 5, seconds = 900, correction = true))
        queue.markSent(inFlight, "w1")
        assertEquals(listOf(900 to true), queue.all().map { it.durationSec to it.correction })
        assertEquals("w1", queue.sentId(inFlight))
    }

    @Test
    fun `a correction for a watch the queue does not know is dropped`() {
        val queue = queue()
        queue.add(report("one", elapsed = 5, seconds = 900, correction = true))
        assertTrue(queue.all().isEmpty())
    }

    @Test
    fun `a correction whose original has no id is dropped without being sent`() {
        val queue = queue()
        queue.add(report("one", elapsed = 5, seconds = 120))
        // The reply carried no id, so the watch can't be named later.
        val transport = FakeTransport(mutableListOf(HttpReply(201, "{}")))
        val uploader = uploader(queue, transport)
        uploader.flush()
        queue.add(report("one", elapsed = 5, seconds = 900, correction = true))
        assertTrue(queue.all().isEmpty())
        assertEquals(FlushResult.Done, uploader.flush())
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `only the last 20 sent ids are kept`() {
        val queue = queue()
        val transport = FakeTransport(MutableList(25) { HttpReply(201, """{"watchEventId":"w$it"}""") })
        repeat(25) { queue.add(report("r$it", elapsed = it.toLong())) }
        uploader(queue, transport).flush()
        assertNull(queue.sentId(report("r4", elapsed = 4)))
        assertEquals("w24", queue.sentId(report("r24", elapsed = 24)))
    }

    @Test
    fun `an empty queue has no first report`() {
        assertNull(queue().first())
    }

    @Test
    fun `the link keeps the token, and a disconnect drops it`() {
        val repo = { TubeGuardLinkRepository(JsonFileStore(File(folder.root, "tubeguard.json"), TubeGuardLink.serializer()) { TubeGuardLink() }) }
        repo().connect("d1", "tok", listOf(TubeGuardChild("c1", "Ana")))
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

    @Test
    fun `the children a reply lists are kept with their ids, and ignored once disconnected`() {
        val repo = { TubeGuardLinkRepository(JsonFileStore(File(folder.root, "tubeguard.json"), TubeGuardLink.serializer()) { TubeGuardLink() }) }
        repo().connect("d1", "tok", listOf(TubeGuardChild("c1", "Ana")))
        repo().updateChildren(listOf(TubeGuardChild("c1", "Ana"), TubeGuardChild("c2", "Ben")))
        assertEquals(listOf(TubeGuardChild("c1", "Ana"), TubeGuardChild("c2", "Ben")), repo().current().childList)
        assertEquals(listOf("Ana", "Ben"), repo().current().children)

        repo().markDisconnected()
        repo().updateChildren(emptyList())
        assertEquals(2, repo().current().childList.size)
    }

    @Test
    fun `a link file from before children had ids still reads as connected`() {
        File(folder.root, "tubeguard.json").writeText("""{"status":"CONNECTED","deviceId":"d1","deviceToken":"tok","children":["Ana"]}""")
        val link = TubeGuardLinkRepository(JsonFileStore(File(folder.root, "tubeguard.json"), TubeGuardLink.serializer()) { TubeGuardLink() }).current()
        assertTrue(link.isConnected)
        assertEquals(emptyList<TubeGuardChild>(), link.childList)
    }

    @Test
    fun `each report the backend takes is passed on with its id and the outcome the reply carried`() {
        val queue = queue()
        queue.add(report("scary"))
        queue.add(report("plain"))
        val over = """{"watchEventId":"w1","outcome":{"decided":true,"outcome":"over","kind":"value","value":"scary","level":"a_lot"}}"""
        val transport = FakeTransport(mutableListOf(HttpReply(201, over), HttpReply(201, """{"watchEventId":"w2","outcome":null}""")))
        val seen = mutableListOf<Triple<String, String?, WatchOutcome?>>()
        uploader(queue, transport, onSent = { r, id, o -> seen += Triple(r.title, id, o) }).flush()
        assertEquals(listOf("scary", "plain"), seen.map { it.first })
        assertEquals(listOf<String?>("w1", "w2"), seen.map { it.second })
        assertTrue(seen[0].third!!.isOver)
        assertNull(seen[1].third)
    }
}
