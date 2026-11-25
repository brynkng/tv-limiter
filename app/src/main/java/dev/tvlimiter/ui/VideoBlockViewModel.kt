package dev.tvlimiter.ui

import dev.tvlimiter.domain.BlockWords
import dev.tvlimiter.domain.BlockedVideo
import dev.tvlimiter.domain.Fetch
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.RequestStatus

enum class VideoStep {
    /** Pick another video, go home, ask a parent, or hand over the remote for the passkey. */
    CHOOSE,
    PIN,

    /** After the passkey: this video, this channel until the TV sleeps, or parent mode until the TV sleeps. */
    ALLOW,

    /** Asked a parent; waiting for the answer. */
    ASKED,
    DENIED,

    /** Let the video play again and close the block screen. */
    RESUME,
}

/** What asking TubeGuard to tell a parent came to. */
sealed interface AskResult {
    data object Sent : AskResult
    data object AlreadyAnswered : AskResult
    data object Unreachable : AskResult
    data object Refused : AskResult

    /** This TV has asked a parent too many times lately. */
    data object TooMany : AskResult
}

/** What recording a passkey approval with TubeGuard came to. */
enum class ApproveResult { Recorded, Banned, Unreachable }

/**
 * Drives the block screen over a paused video. Like the time-limit block screen, a child can only leave it or hand
 * the remote to a parent. Every side effect is a function passed in, so this holds no Android types.
 *
 * Waiting for a parent keeps the screen up and asks TubeGuard every five seconds ([pollResult]). It also notices
 * a video that stopped being blocked while the screen was up (a parent approved it or lifted a ban).
 */
class VideoBlockViewModel(
    video: BlockedVideo,
    private val guard: PinGuard,
    private val allowVideo: () -> Unit,
    private val allowChannel: () -> Unit,
    private val startParentMode: () -> Unit,
) {
    /** The video being covered, with the reason TubeGuard last gave for it. */
    var video: BlockedVideo = video
        private set

    var step: VideoStep = VideoStep.CHOOSE
        private set

    /** A short line under the buttons, for something that didn't work. */
    var notice: String? = null
        private set

    val headline: String get() = BlockWords.HEADLINE

    /** In Shorts there is no other video to pick: the child leaves Shorts with Back. */
    val pickAnotherLabel: String get() = if (video.isShort) "Leave Shorts" else "Pick another video"
    val pickAnotherHint: String get() = if (video.isShort) "Press Back to leave Shorts." else "Press Back to choose another video."

    /** A Short has no channel to allow, so the parent is not offered it. */
    val canAllowChannel: Boolean get() = !video.isShort
    val reason: String get() = video.reason

    /** Whether the polling clock should be running. */
    val polling: Boolean get() = step == VideoStep.CHOOSE || step == VideoStep.ASKED || step == VideoStep.DENIED

    fun requestPin() {
        if (step == VideoStep.CHOOSE || step == VideoStep.ASKED || step == VideoStep.DENIED) {
            notice = null
            step = VideoStep.PIN
        }
    }

    fun cancelPin() {
        if (step == VideoStep.PIN) step = VideoStep.CHOOSE
    }

    /** The slow part, safe off the main thread: it only reads and writes the passkey file. */
    fun checkPin(pin: String): PinCheck = guard.check(pin)

    fun applyCheck(check: PinCheck) {
        if (check == PinCheck.Ok && step == VideoStep.PIN) step = VideoStep.ALLOW
    }

    fun askResult(result: AskResult) {
        notice = null
        when (result) {
            AskResult.Sent -> step = VideoStep.ASKED
            AskResult.AlreadyAnswered -> Unit
            AskResult.Unreachable -> notice = "Couldn't reach the parents' app. Try again."
            AskResult.Refused -> notice = "A parent can't be asked about this one."
            AskResult.TooMany -> notice = "You have asked a lot already. Try again later."
        }
    }

    /** A parent's answer, or a change to the video's outcome, read from TubeGuard. */
    fun pollResult(fetched: Fetch) {
        if (step != VideoStep.CHOOSE && step != VideoStep.ASKED && step != VideoStep.DENIED) return
        val outcome = (fetched as? Fetch.Got)?.outcome ?: return
        // TubeGuard no longer has a decided answer that blocks this video (for example it is being checked again
        // after a ban was removed): the block was made on old news, so it is lifted rather than held.
        if (!outcome.decided) return resume()
        if (outcome.isOver) video = video.copy(kind = outcome.kind ?: video.kind, value = outcome.value, level = outcome.level, ageBand = outcome.ageBand)
        when {
            // Approved, a ban removed, or limits changed: it is not over a limit any more.
            !outcome.isOver -> resume()
            outcome.request?.status == RequestStatus.APPROVED -> resume()
            outcome.request?.status == RequestStatus.DENIED && step == VideoStep.ASKED -> {
                step = VideoStep.DENIED
                notice = null
            }
        }
    }

    /** After the passkey: [result] is how recording the approval with TubeGuard went. */
    fun allowThisVideo(result: ApproveResult) {
        if (step != VideoStep.ALLOW) return
        when (result) {
            // A parent at the TV with the passkey always wins. A ban TubeGuard keeps (one for all children, or on
            // the whole channel) stays there, and the video plays here until the TV sleeps. So does a video
            // allowed without a connection.
            ApproveResult.Banned -> allowVideo()
            ApproveResult.Unreachable -> allowVideo()
            ApproveResult.Recorded -> allowVideo()
        }
        resume()
    }

    fun allowThisChannel() {
        if (step != VideoStep.ALLOW || !canAllowChannel) return
        allowChannel()
        resume()
    }

    fun parentMode() {
        if (step != VideoStep.ALLOW) return
        startParentMode()
        resume()
    }

    private fun resume() {
        step = VideoStep.RESUME
    }
}
