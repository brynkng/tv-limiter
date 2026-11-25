package dev.tvlimiter.ui

import dev.tvlimiter.domain.PairResult
import dev.tvlimiter.domain.PairingCodeEntry

/**
 * Drives the "Connect to TubeGuard" screen: the code typed so far, whether it is being checked, and what to say
 * when it didn't work. Checking the code is a network call, so the screen runs it off the main thread between
 * [beginCheck] and [finishCheck].
 */
class PairingViewModel {
    sealed interface Status {
        data object Entering : Status
        data object Checking : Status
        data class Connected(val children: List<String>) : Status
        data class Problem(val message: String) : Status
    }

    val entry = PairingCodeEntry()
    var status: Status = Status.Entering
        private set

    private val canType: Boolean get() = status == Status.Entering || status is Status.Problem

    fun press(digit: Int) {
        if (!canType) return
        if (entry.isComplete) {
            // A digit never replaces one silently; say how to fix a mistake instead.
            status = Status.Problem("Press Delete to change a digit.")
            return
        }
        entry.press(digit)
        status = Status.Entering
    }

    fun delete() {
        if (!canType) return
        entry.delete()
        status = Status.Entering
    }

    /** The finished code to check, or null when it isn't complete or a check is already running. */
    fun beginCheck(): String? {
        if (!canType || !entry.isComplete) return null
        status = Status.Checking
        return entry.text
    }

    fun finishCheck(result: PairResult) {
        if (status != Status.Checking) return
        status = when (result) {
            is PairResult.Paired -> Status.Connected(result.children.map { it.name })
            PairResult.InvalidCode -> Status.Problem("That code didn't work. Check the digits (Delete fixes one) and try again.")
            PairResult.CodeUsed -> newCodeNeeded("That code was already used to connect a TV.")
            PairResult.CodeExpired -> newCodeNeeded("That code has expired. Codes work for 15 minutes.")
            PairResult.TooManyAttempts -> newCodeNeeded("That code has been tried too many times.")
            PairResult.RateLimited -> {
                entry.clear()
                Status.Problem("Too many tries from this TV's network. Wait 15 minutes, then try again.")
            }
            PairResult.Unreachable -> Status.Problem("Can't reach TubeGuard. Check the TV's internet connection, then try again.")
        }
    }

    private fun newCodeNeeded(why: String): Status {
        entry.clear()
        return Status.Problem("$why Get a new code in the TubeGuard parent app.")
    }

    companion object {
        /** "Ana", "Ana and Ben", "Ana, Ben and Cy". */
        fun names(children: List<String>): String = when (children.size) {
            0 -> "your family"
            1 -> children[0]
            else -> children.dropLast(1).joinToString(", ") + " and " + children.last()
        }
    }
}
