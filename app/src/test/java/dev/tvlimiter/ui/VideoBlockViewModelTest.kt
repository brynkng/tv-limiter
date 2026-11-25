package dev.tvlimiter.ui

import dev.tvlimiter.domain.ApprovalRequest
import dev.tvlimiter.domain.BlockedVideo
import dev.tvlimiter.domain.Fetch
import dev.tvlimiter.domain.InMemoryPinStore
import dev.tvlimiter.domain.OverKind
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.PinHasher
import dev.tvlimiter.domain.RequestStatus
import dev.tvlimiter.domain.VideoKey
import dev.tvlimiter.domain.WatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBlockViewModelTest {
    private val guard = PinGuard(InMemoryPinStore(), PinHasher(iterations = 1_000)) { 0L }.apply { setPin("2468") }
    private val video = BlockedVideo(VideoKey.of("Haunted Castle", "Spooky TV", 600), OverKind.VALUE, "scary", "a_lot", "w1")
    private var videoAllowed = 0
    private var channelAllowed = 0
    private var parentMode = 0

    private fun model() = VideoBlockViewModel(video, guard, { videoAllowed++ }, { channelAllowed++ }, { parentMode++ })

    private fun outcome(name: String, request: ApprovalRequest? = null) = Fetch.Got(WatchOutcome(true, name, OverKind.VALUE, "scary", "a_lot", request))

    private fun asked(model: VideoBlockViewModel) = model.apply { askResult(AskResult.Sent) }

    @Test
    fun `the screen says why in a child's words`() {
        val m = model()
        assertEquals("This video is blocked", m.headline)
        assertEquals("This video has a lot of scary or creepy parts.", m.reason)
        assertEquals(VideoStep.CHOOSE, m.step)
    }

    @Test
    fun `asking a parent moves to waiting, and a failure says so without leaving the screen`() {
        val m = model()
        m.askResult(AskResult.Unreachable)
        assertEquals(VideoStep.CHOOSE, m.step)
        assertNotNull(m.notice)
        m.askResult(AskResult.Sent)
        assertEquals(VideoStep.ASKED, m.step)
        assertNull(m.notice)
    }

    @Test
    fun `too many requests and a refusal are explained`() {
        val m = model()
        m.askResult(AskResult.TooMany)
        assertTrue(m.notice!!.contains("asked a lot"))
        m.askResult(AskResult.Refused)
        assertTrue(m.notice!!.contains("can't be asked"))
        assertEquals(VideoStep.CHOOSE, m.step)
    }

    @Test
    fun `while waiting, a pending answer changes nothing`() {
        val m = asked(model())
        m.pollResult(outcome("over", ApprovalRequest("r1", RequestStatus.PENDING)))
        assertEquals(VideoStep.ASKED, m.step)
    }

    @Test
    fun `an approval resumes the video`() {
        val m = asked(model())
        m.pollResult(outcome("within", ApprovalRequest("r1", RequestStatus.APPROVED)))
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `an approved request resumes even while the outcome still reads over`() {
        val m = asked(model())
        m.pollResult(outcome("over", ApprovalRequest("r1", RequestStatus.APPROVED)))
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `a denial is shown and the child can still pick another video or go home`() {
        val m = asked(model())
        m.pollResult(outcome("over", ApprovalRequest("r1", RequestStatus.DENIED)))
        assertEquals(VideoStep.DENIED, m.step)
        assertTrue(m.polling)
    }

    @Test
    fun `an unreachable or undecided poll changes nothing`() {
        val m = asked(model())
        m.pollResult(Fetch.Unreachable)
        m.pollResult(Fetch.Gone)
        m.pollResult(Fetch.Got(WatchOutcome(false, null, null, null, null, null)))
        assertEquals(VideoStep.ASKED, m.step)
    }

    @Test
    fun `a ban lifted or an approval made in the app while the screen is up resumes the video`() {
        val m = model()
        m.pollResult(outcome("within"))
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `a video still over a limit keeps the screen up`() {
        val m = model()
        m.pollResult(outcome("over"))
        assertEquals(VideoStep.CHOOSE, m.step)
    }

    @Test
    fun `the right passkey opens the three choices, a wrong one does not`() {
        val m = model()
        m.requestPin()
        assertEquals(VideoStep.PIN, m.step)
        m.applyCheck(m.checkPin("1111"))
        assertEquals(VideoStep.PIN, m.step)
        assertEquals(PinCheck.Ok, m.checkPin("2468"))
        m.applyCheck(PinCheck.Ok)
        assertEquals(VideoStep.ALLOW, m.step)
    }

    @Test
    fun `five wrong passkeys lock the pad, as on the time limit screen`() {
        val m = model()
        m.requestPin()
        repeat(4) { assertTrue(m.checkPin("1111") is PinCheck.Wrong) }
        assertTrue(m.checkPin("1111") is PinCheck.LockedOut)
        assertTrue(m.checkPin("2468") is PinCheck.LockedOut)
    }

    private fun unlocked(): VideoBlockViewModel = model().apply {
        requestPin()
        applyCheck(checkPin("2468"))
    }

    @Test
    fun `allow this video records it and resumes`() {
        val m = unlocked()
        m.allowThisVideo(ApproveResult.Recorded)
        assertEquals(1, videoAllowed)
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `allow this video without a connection still allows it until the TV sleeps`() {
        val m = unlocked()
        m.allowThisVideo(ApproveResult.Unreachable)
        assertEquals(1, videoAllowed)
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `the passkey plays a video TubeGuard keeps banned, until the TV sleeps`() {
        val m = unlocked()
        m.allowThisVideo(ApproveResult.Banned)
        assertEquals(1, videoAllowed)
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `allow this channel is local and resumes`() {
        val m = unlocked()
        m.allowThisChannel()
        assertEquals(1, channelAllowed)
        assertEquals(0, videoAllowed)
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `parent mode starts parent mode and resumes`() {
        val m = unlocked()
        m.parentMode()
        assertEquals(1, parentMode)
        assertEquals(VideoStep.RESUME, m.step)
    }

    @Test
    fun `the allow choices do nothing before the passkey`() {
        val m = model()
        m.allowThisVideo(ApproveResult.Recorded)
        m.allowThisChannel()
        m.parentMode()
        assertEquals(0, videoAllowed + channelAllowed + parentMode)
        assertEquals(VideoStep.CHOOSE, m.step)
    }

    @Test
    fun `backing out of the passkey returns to the choices`() {
        val m = model()
        m.requestPin()
        m.cancelPin()
        assertEquals(VideoStep.CHOOSE, m.step)
    }

    @Test
    fun `a parent can enter the passkey while a request waits`() {
        val m = asked(model())
        m.requestPin()
        assertEquals(VideoStep.PIN, m.step)
    }
}
