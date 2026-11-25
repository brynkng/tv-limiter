package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeWireTest {
    @Test
    fun `an over outcome is read from a report reply`() {
        val o = OutcomeWire.parse(
            """{"watchEventId":"w1","watchTime":5,"approximate":false,
               "outcome":{"decided":true,"outcome":"over","kind":"value","reason":"x","value":"scary","level":"a_lot","ageBand":"13+","request":null}}""",
        )!!
        assertTrue(o.isOver)
        assertEquals(OverKind.VALUE, o.kind)
        assertEquals("scary", o.value)
        assertEquals("a_lot", o.level)
        assertNull(o.request)
    }

    @Test
    fun `a reply with no outcome yet gives none`() {
        assertNull(OutcomeWire.parse("""{"watchEventId":"w1","outcome":null}"""))
        assertNull(OutcomeWire.parse("""{"watchEventId":"w1"}"""))
        assertNull(OutcomeWire.parse("not json"))
        assertNull(OutcomeWire.parse(""))
    }

    @Test
    fun `an outcome still being decided is not over`() {
        val o = OutcomeWire.parse("""{"outcome":{"decided":false,"outcome":null,"kind":null,"value":null,"level":null,"request":null}}""")!!
        assertFalse(o.decided)
        assertFalse(o.isOver)
    }

    @Test
    fun `near and within are never over`() {
        for (name in listOf("near", "within", "not_checked", "unidentified")) {
            assertFalse(name, OutcomeWire.parse("""{"outcome":{"decided":true,"outcome":"$name"}}""")!!.isOver)
        }
    }

    @Test
    fun `each kind is read, and an unknown one is other`() {
        val kinds = mapOf(
            "value" to OverKind.VALUE, "worldview" to OverKind.WORLDVIEW, "video_ban" to OverKind.VIDEO_BAN,
            "channel_ban" to OverKind.CHANNEL_BAN, "learned_match" to OverKind.LEARNED_MATCH,
            "age_restricted" to OverKind.AGE_RESTRICTED, "something_new" to OverKind.OTHER,
        )
        kinds.forEach { (wire, kind) ->
            assertEquals(wire, kind, OutcomeWire.parse("""{"outcome":{"decided":true,"outcome":"over","kind":"$wire"}}""")!!.kind)
        }
    }

    @Test
    fun `the request and its status are read`() {
        val body = { status: String -> """{"outcome":{"decided":true,"outcome":"over","request":{"id":"r1","status":"$status"}}}""" }
        assertEquals(ApprovalRequest("r1", RequestStatus.PENDING), OutcomeWire.parse(body("pending"))!!.request)
        assertEquals(RequestStatus.APPROVED, OutcomeWire.parse(body("approved"))!!.request!!.status)
        assertEquals(RequestStatus.DENIED, OutcomeWire.parse(body("denied"))!!.request!!.status)
        assertNull(OutcomeWire.parse(body("surprising"))!!.request)
    }

    @Test
    fun `the answer to asking a parent is read`() {
        assertEquals(ApprovalRequest("r9", RequestStatus.PENDING), OutcomeWire.parseRequest("""{"requestId":"r9","status":"pending"}"""))
        assertNull(OutcomeWire.parseRequest("""{"status":"pending"}"""))
    }

    @Test
    fun `paths name the watch and are safe to send`() {
        assertEquals("/api/device/watches/abc", TubeGuardWire.watchPath("abc"))
        assertEquals("/api/device/watches/abc/request-approval", TubeGuardWire.requestApprovalPath("abc"))
        assertEquals("/api/device/watches/abc/approve", TubeGuardWire.approvePath("abc"))
        assertEquals("/api/device/watches/a%2Fb", TubeGuardWire.watchPath("a/b"))
    }
}
