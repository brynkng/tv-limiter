package dev.tvlimiter.ui

import dev.tvlimiter.domain.PinCheck

/** The line shown under the passkey circles after an attempt, shared by every PIN prompt. */
fun pinFeedback(check: PinCheck): String? = when (check) {
    PinCheck.Ok -> null
    PinCheck.NoPin -> "No passkey is set yet"
    PinCheck.Damaged -> "The saved passkey is damaged. Clear this app's storage in Android Settings, then set a new one."
    is PinCheck.Wrong -> if (check.triesLeft == 1) "Wrong passkey, 1 try left" else "Wrong passkey, ${check.triesLeft} tries left"
    is PinCheck.LockedOut -> lockoutMessage(check.secondsLeft)
}

fun lockoutMessage(secondsLeft: Int) = "Try again in ${secondsLeft}s"
