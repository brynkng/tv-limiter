package dev.tvlimiter.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.Profiles

/**
 * Chooses the parent passkey, entered twice. On first launch that's all. To change an existing
 * passkey, the current one is asked for first, so a child who finds the settings left open
 * can't replace it. A child's passkey is set from the parent's settings, which the parent
 * passkey already unlocked, so it is never asked for first.
 */
class PinSetupActivity : TrackedActivity() {
    private enum class Step { CURRENT, NEW, CONFIRM }

    private lateinit var pad: PinPadView
    private val title by lazy { label("", sizeSp = 28f, bold = true) }
    private var changing = false
    /** The child whose passkey this sets, or null for the parent passkey. */
    private var childName: String? = null
    private var step = Step.NEW
    private var first: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = intent.getStringExtra(EXTRA_PROFILE) ?: Profiles.SHARED_ID
        childName = intent.getStringExtra(EXTRA_NAME).takeIf { profileId != Profiles.SHARED_ID }
        val guard = PinRepository.guard(this, profileId)
        changing = childName == null && intent.getBooleanExtra(EXTRA_CHANGE, false)
        step = if (changing) Step.CURRENT else Step.NEW
        pad = PinPadView(this)
        pad.bind(lockoutSeconds = { if (step == Step.CURRENT) guard.lockoutSecondsLeft() else 0 }) { pin ->
            // Checking and saving both hash the passkey, so they run here, off the main thread.
            val entered = step
            val check = if (entered == Step.CURRENT) guard.check(pin) else null
            val saved = entered == Step.CONFIRM && first == pin && run { guard.setPin(pin); true }
            val show = { onEntered(entered, pin, check, saved) }
            show
        }
        val note = when {
            childName != null -> "$childName will press these arrows to start watching."
            changing -> null
            else -> "Your child will only see dots, never which buttons you press."
        }
        setContentView(pinScreen(titleFor(step), pad, title, note = note))
    }

    private fun onEntered(entered: Step, pin: String, check: PinCheck?, saved: Boolean) {
        when (entered) {
            Step.CURRENT -> if (check == PinCheck.Ok) goTo(Step.NEW) else check?.let { pad.showResult(it) }
            Step.NEW -> {
                first = pin
                goTo(Step.CONFIRM)
            }
            Step.CONFIRM -> if (saved) {
                SettingsUnlock.shared.unlock()
                setResult(RESULT_OK)
                finishIntoSkeleton(Skeleton.LIST)
            } else {
                first = null
                step = Step.NEW
                title.text = titleFor(step)
                pad.open("Those did not match. Start again.")
            }
        }
    }

    private fun goTo(next: Step) {
        step = next
        title.text = titleFor(next)
        pad.open()
    }

    private fun titleFor(step: Step) = childName?.let { name ->
        if (step == Step.CONFIRM) "Enter $name's passkey again" else "Choose a passkey for $name"
    } ?: when (step) {
        Step.CURRENT -> "Enter your current passkey"
        Step.NEW -> if (changing) "Choose a new passkey" else "Choose a parent passkey"
        Step.CONFIRM -> if (changing) "Enter the new passkey again" else "Enter the same passkey again"
    }

    @Deprecated("Back clears a partial entry, then steps back to the new passkey, then leaves")
    override fun onBackPressed() {
        if (pad.handleBack()) return
        if (step == Step.CONFIRM) {
            first = null
            goTo(Step.NEW)
            return
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val EXTRA_CHANGE = "change"
        private const val EXTRA_PROFILE = "profile"
        private const val EXTRA_NAME = "name"

        /** Opens the screen that replaces the current passkey after asking for it. */
        fun changeIntent(context: Context): Intent =
            Intent(context, PinSetupActivity::class.java).putExtra(EXTRA_CHANGE, true)

        /** Opens the screen that sets or replaces one child's passkey. */
        fun profileIntent(context: Context, profileId: String, name: String): Intent =
            Intent(context, PinSetupActivity::class.java).putExtra(EXTRA_PROFILE, profileId).putExtra(EXTRA_NAME, name)
    }
}
