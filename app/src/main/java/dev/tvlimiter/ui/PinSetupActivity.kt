package dev.tvlimiter.ui

import android.os.Bundle
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.domain.PinGuard

/** First launch: choose a PIN of 4 to 8 digits, entered twice on the scrambled pad. */
class PinSetupActivity : TrackedActivity() {
    private lateinit var pad: PinPadView
    private val title by lazy { label("", sizeSp = 28f, bold = true) }
    private var first: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val guard = PinRepository.guard(this)
        pad = PinPadView(this)
        pad.bind(lockoutSeconds = { 0 }) { pin -> onEntered(guard, pin) }
        setContentView(pinScreen(FIRST_TITLE, pad, title))
    }

    private fun onEntered(guard: PinGuard, pin: String) {
        val chosen = first
        when {
            chosen == null -> {
                first = pin
                title.text = "Enter the same PIN again"
                pad.open()
            }
            chosen == pin -> {
                guard.setPin(pin)
                SettingsUnlock.shared.unlock()
                setResult(RESULT_OK)
                finish()
            }
            else -> {
                first = null
                title.text = FIRST_TITLE
                pad.open("PINs did not match")
            }
        }
    }

    @Deprecated("Back clears a partial entry, then steps back to the first entry, then leaves")
    override fun onBackPressed() {
        if (pad.handleBack()) return
        if (first != null) {
            first = null
            title.text = FIRST_TITLE
            pad.open()
            return
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    private companion object {
        const val FIRST_TITLE = "Choose a PIN of 4 to 8 digits"
    }
}
