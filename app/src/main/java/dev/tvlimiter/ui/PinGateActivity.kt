package dev.tvlimiter.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import dev.tvlimiter.R
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.domain.PinCheck

/** Asks for the PIN before the limits can be changed. */
class PinGateActivity : TrackedActivity() {
    private lateinit var pad: PinPadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val guard = PinRepository.guard(this)
        pad = PinPadView(this)
        pad.bind(lockoutSeconds = guard::lockoutSecondsLeft) { pin ->
            val check = guard.check(pin)
            if (check == PinCheck.Ok) {
                SettingsUnlock.shared.unlock()
                setResult(RESULT_OK)
                finish()
            } else {
                pad.showResult(check)
            }
        }
        setContentView(pinScreen("Enter the PIN to change limits", pad))
    }

    @Deprecated("Back clears a partial entry before leaving")
    override fun onBackPressed() {
        if (pad.handleBack()) return
        setResult(RESULT_CANCELED)
        finish()
    }
}

/** A centered title above a PIN pad, shared by the PIN screens. */
internal fun android.content.Context.pinScreen(title: String, pad: PinPadView, titleView: android.widget.TextView? = null) =
    FrameLayout(this).apply {
        setBackgroundColor(color(R.color.bg))
        val heading = titleView ?: label(title, sizeSp = 28f, bold = true)
        heading.text = title
        heading.gravity = Gravity.CENTER
        val content = column(Gravity.CENTER_HORIZONTAL).apply {
            addView(heading)
            addView(pad.also { it.margins(this@pinScreen, top = 32) })
        }
        addView(content, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }
