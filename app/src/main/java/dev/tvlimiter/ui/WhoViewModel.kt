package dev.tvlimiter.ui

import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.Profile

enum class WhoStep { CHOOSE, PIN, DONE }

/** Who a passkey is being asked from on the picker: one child, or the parent. */
sealed interface WhoChoice {
    data class Child(val profile: Profile) : WhoChoice
    data object Parent : WhoChoice
}

/**
 * Drives the "Who's watching" screen. A young child without a passkey starts at once; any
 * other child enters their own passkey, and the Parent tile takes the parent passkey and
 * starts parent mode.
 */
class WhoViewModel(
    val profiles: List<Profile>,
    private val hasPasskey: (profileId: String) -> Boolean,
    private val childGuard: (profileId: String) -> PinGuard,
    private val parentGuard: PinGuard,
    private val startViewer: (profileId: String) -> Unit,
    private val startParentMode: () -> Unit,
) {
    var step = WhoStep.CHOOSE
        private set
    var choice: WhoChoice? = null
        private set

    val title: String
        get() = when (val picked = choice) {
            null -> "Who's watching?"
            is WhoChoice.Child -> "${picked.profile.name}, enter your passkey"
            WhoChoice.Parent -> "Parent passkey"
        }

    fun pick(next: WhoChoice) {
        if (step != WhoStep.CHOOSE) return
        choice = next
        if (next is WhoChoice.Child && next.profile.startsWithoutPasskey(hasPasskey(next.profile.id))) {
            startViewer(next.profile.id)
            step = WhoStep.DONE
        } else {
            step = WhoStep.PIN
        }
    }

    fun cancelPin() {
        if (step != WhoStep.PIN) return
        step = WhoStep.CHOOSE
        choice = null
    }

    /** The lockout of whichever passkey is being asked for. */
    fun lockoutSecondsLeft(): Int = guard()?.lockoutSecondsLeft() ?: 0

    /** The slow part, safe to run off the main thread: it only reads and writes the passkey file. */
    fun checkPin(pin: String): PinCheck = guard()?.check(pin) ?: PinCheck.NoPin

    fun applyCheck(check: PinCheck) {
        if (check != PinCheck.Ok || step != WhoStep.PIN) return
        when (val picked = choice) {
            is WhoChoice.Child -> startViewer(picked.profile.id)
            WhoChoice.Parent -> startParentMode()
            null -> return
        }
        step = WhoStep.DONE
    }

    private fun guard(): PinGuard? = when (val picked = choice) {
        is WhoChoice.Child -> childGuard(picked.profile.id)
        WhoChoice.Parent -> parentGuard
        null -> null
    }
}
