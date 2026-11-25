package dev.tvlimiter.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TubeGuardWireTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val report = QueuedReport("A title", "A channel", 213, startElapsedMs = 10_000, startWallMs = 5_000_000, bootCount = 7)

    @Test
    fun `a report body has exactly the fields the backend reads`() {
        val body = obj(TubeGuardWire.reportBody(report, Moment(70_000, 5_060_000), currentBootCount = 7))
        assertEquals(setOf("title", "channel", "app", "ageMs", "bootCount", "currentBootCount", "durationSec"), body.keys)
        assertEquals("youtube", body["app"]!!.jsonPrimitive.content)
        assertEquals("A title", body["title"]!!.jsonPrimitive.content)
        assertEquals(213, body["durationSec"]!!.jsonPrimitive.int)
        assertEquals(7, body["bootCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a report carries the linked child's id, and none when the profile has no link`() {
        val linked = obj(TubeGuardWire.reportBody(report.copy(childId = "c1"), Moment(70_000, 5_060_000), currentBootCount = 7))
        assertEquals("c1", linked["childId"]!!.jsonPrimitive.content)
        val unlinked = obj(TubeGuardWire.reportBody(report, Moment(70_000, 5_060_000), currentBootCount = 7))
        assertFalse(unlinked.containsKey("childId"))
    }

    @Test
    fun `a queued watch keeps the child it was linked to`() {
        val watch = Watch("A title", "A channel", 60, Moment(1, 2), profileId = "p1")
        assertEquals("c9", TubeGuardWire.queued(watch, 3, "c9").childId)
        assertEquals(null, TubeGuardWire.queued(watch, 3).childId)
    }

    @Test
    fun `a check-in reply lists the children, and one that doesn't leaves them alone`() {
        assertEquals(
            listOf(TubeGuardChild("c1", "Ana"), TubeGuardChild("c2", "Ben")),
            TubeGuardWire.children("""{"serverTime":1,"children":[{"id":"c1","name":"Ana"},{"id":"c2","name":"Ben"}]}"""),
        )
        assertEquals(null, TubeGuardWire.children("""{"serverTime":1}"""))
        assertEquals(null, TubeGuardWire.children("not json"))
    }

    @Test
    fun `within one boot the age comes from the elapsed clock and ignores the wall clock`() {
        // The wall clock was set back a year; the elapsed clock still says one minute.
        val body = obj(TubeGuardWire.reportBody(report, Moment(70_000, 5_000_000 - 31_536_000_000), currentBootCount = 7))
        assertEquals(60_000, body["ageMs"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `after a reboot the report keeps its old boot count and a wall clock age`() {
        // Elapsed time started over at the reboot, so its reading is meaningless for this report.
        val body = obj(TubeGuardWire.reportBody(report, Moment(3_000, 5_000_000 + 3_600_000), currentBootCount = 8))
        assertEquals(3_600_000, body["ageMs"]!!.jsonPrimitive.content.toLong())
        assertEquals(7, body["bootCount"]!!.jsonPrimitive.int)
        assertEquals(8, body["currentBootCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `an age is never negative`() {
        val body = obj(TubeGuardWire.reportBody(report, Moment(1_000, 1_000), currentBootCount = 8))
        assertEquals(0, body["ageMs"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a report with no length leaves the length out`() {
        val body = obj(TubeGuardWire.reportBody(report.copy(durationSec = null), Moment(70_000, 0), 7))
        assertFalse("durationSec" in body)
    }

    @Test
    fun `a report has no profile, video id or device clock`() {
        val body = TubeGuardWire.reportBody(report, Moment(70_000, 0), 7)
        listOf("profile", "videoId", "deviceClock", "child").forEach { assertFalse(it, body.contains(it)) }
    }

    @Test
    fun `a long title and channel are cut to the backend's limits`() {
        val body = obj(TubeGuardWire.reportBody(report.copy(title = "t".repeat(900), channel = "c".repeat(400)), Moment(1, 1), 7))
        assertEquals(TubeGuardWire.MAX_TITLE, body["title"]!!.jsonPrimitive.content.length)
        assertEquals(TubeGuardWire.MAX_CHANNEL, body["channel"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun `a watch without a channel is sent with a placeholder, since the backend needs one`() {
        val queued = TubeGuardWire.queued(Watch("T", "", null, Moment(1, 2)), bootCount = 3)
        assertEquals(TubeGuardWire.UNKNOWN_CHANNEL, queued.channel)
        assertEquals(3, queued.bootCount)
    }

    @Test
    fun `a correction body names the watch it replaces, and a plain one does not`() {
        val body = obj(TubeGuardWire.reportBody(report, Moment(70_000, 5_060_000), 7, replaces = "w1"))
        assertEquals("w1", body["replaces"]!!.jsonPrimitive.content)
        assertFalse(obj(TubeGuardWire.reportBody(report, Moment(70_000, 5_060_000), 7)).containsKey("replaces"))
    }

    @Test
    fun `a corrected watch is queued as a correction with the same key`() {
        val watch = Watch("T", "C", 900, Moment(10_000, 2), correction = true)
        val queued = TubeGuardWire.queued(watch, bootCount = 3)
        assertTrue(queued.correction)
        assertEquals(TubeGuardWire.queued(watch.copy(correction = false), 3).key, queued.key)
    }

    @Test
    fun `the watch id is read from the reply, and a reply without one gives none`() {
        assertEquals("w1", TubeGuardWire.watchEventId("""{"watchEventId":"w1","watchTime":5}"""))
        assertEquals(null, TubeGuardWire.watchEventId("{}"))
        assertEquals(null, TubeGuardWire.watchEventId("not json"))
    }

    @Test
    fun `the pairing and check in bodies carry only what the contract needs`() {
        val pair = obj(TubeGuardWire.pairBody("12345678", "Living room TV", "America/Denver", "1.2.0"))
        assertEquals(setOf("code", "name", "platform", "timeZone", "appVersion"), pair.keys)
        assertEquals("tv", pair["platform"]!!.jsonPrimitive.content)
        val beat = obj(TubeGuardWire.heartbeatBody(listenerEnabled = false, appVersion = "1.2.0"))
        assertEquals(setOf("listenerEnabled", "appVersion"), beat.keys)
        assertEquals("false", beat["listenerEnabled"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a blank device name falls back to TV`() {
        assertEquals("TV", obj(TubeGuardWire.pairBody("12345678", "  ", "UTC", "1"))["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a pairing success names the children and keeps the token`() {
        val result = TubeGuardWire.pairResult(
            201,
            """{"deviceId":"d1","deviceToken":"secret","children":[{"id":"c1","name":"Ana"},{"id":"c2","name":"Ben"}]}""",
        )
        assertEquals(PairResult.Paired("d1", "secret", listOf(TubeGuardChild("c1", "Ana"), TubeGuardChild("c2", "Ben"))), result)
        assertFalse("the token must not show in logs", result.toString().contains("secret"))
    }

    @Test
    fun `pairing errors map to what the screen says`() {
        assertEquals(PairResult.InvalidCode, TubeGuardWire.pairResult(400, """{"error":"invalid_code"}"""))
        assertEquals(PairResult.CodeUsed, TubeGuardWire.pairResult(400, """{"error":"code_used"}"""))
        assertEquals(PairResult.CodeExpired, TubeGuardWire.pairResult(400, """{"error":"code_expired"}"""))
        assertEquals(PairResult.TooManyAttempts, TubeGuardWire.pairResult(400, """{"error":"too_many_attempts"}"""))
        assertEquals(PairResult.InvalidCode, TubeGuardWire.pairResult(400, "not json"))
        assertEquals(PairResult.RateLimited, TubeGuardWire.pairResult(429, """{"error":"rate_limited"}"""))
        assertEquals(PairResult.Unreachable, TubeGuardWire.pairResult(503, ""))
        assertEquals(PairResult.Unreachable, TubeGuardWire.pairResult(201, "not json"))
        assertEquals(PairResult.Unreachable, TubeGuardWire.pairResult(201, """{"deviceId":"d1"}"""))
    }

    @Test
    fun `status codes map to what happens to a report`() {
        assertEquals(UploadOutcome.Sent, UploadOutcome.of(201))
        assertEquals(UploadOutcome.Disconnected, UploadOutcome.of(401))
        assertEquals(UploadOutcome.Retry, UploadOutcome.of(429))
        assertEquals(UploadOutcome.Retry, UploadOutcome.of(500))
        assertEquals(UploadOutcome.Retry, UploadOutcome.of(503))
        assertEquals(UploadOutcome.Rejected, UploadOutcome.of(400))
        assertEquals(UploadOutcome.Rejected, UploadOutcome.of(422))
    }

    @Test
    fun `only YouTube apps are followed`() {
        assertTrue(YouTubePackages.isYouTube("com.google.android.youtube.tv"))
        assertTrue(YouTubePackages.isYouTube("com.google.android.youtube"))
        assertTrue(YouTubePackages.isYouTube("com.amazon.firetv.youtube"))
        assertFalse(YouTubePackages.isYouTube("com.netflix.ninja"))
        assertFalse(YouTubePackages.isYouTube("com.android.chrome"))
    }

    @Test
    fun `a link prints without its token`() {
        val link = TubeGuardLink(LinkStatus.CONNECTED, "d1", "secret", listOf("Ana"))
        assertFalse(link.toString().contains("secret"))
    }
}
