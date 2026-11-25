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
    val video: BlockedVideo,
    private val guard: PinGuard,
    private val allowVideo: () -> Unit,
    private val allowChannel: () -> Unit,
    private val startParentMode: () -> Unit,
) {
    var step: VideoStep = VideoStep.CHOOSE
        private set

    /** A short line under the buttons, for something that didn't work. */
    var notice: String? = null
        private set

    val headline: String get() = BlockWords.HEADLINE
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
        if (!outcome.decided) return
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
        if (step != VideoStep.ALLOW) return
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
