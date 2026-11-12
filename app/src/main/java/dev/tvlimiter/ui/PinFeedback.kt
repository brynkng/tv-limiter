package dev.tvlimiter.ui

import dev.tvlimiter.domain.PinCheck

/** The line shown under the arrow field after a PIN attempt, shared by every PIN prompt. */
fun pinFeedback(check: PinCheck): String? = when (check) {
    PinCheck.Ok -> null
    PinCheck.NoPin -> "No PIN is set yet"
    is PinCheck.Wrong -> if (check.triesLeft == 1) "Wrong PIN, 1 try left" else "Wrong PIN, ${check.triesLeft} tries left"
    is PinCheck.LockedOut -> lockoutMessage(check.secondsLeft)
}

fun lockoutMessage(secondsLeft: Int) = "Try again in ${secondsLeft}s"
