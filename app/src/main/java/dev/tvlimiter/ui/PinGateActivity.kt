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
            val show = {
                if (check == PinCheck.Ok) {
                    SettingsUnlock.shared.unlock()
                    setResult(RESULT_OK)
                    finishIntoSkeleton(Skeleton.from(intent))
                } else {
                    pad.showResult(check)
                }
            }
            show
        }
        setContentView(pinScreen("Parent passkey", pad, note = "Enter it to change your child's limits."))
    }

    @Deprecated("Back clears a partial entry before leaving")
    override fun onBackPressed() {
        if (pad.handleBack()) return
        setResult(RESULT_CANCELED)
        finish()
    }
}

/** A centered title above a PIN pad, shared by the PIN screens. */
internal fun android.content.Context.pinScreen(
    title: String,
    pad: PinPadView,
    titleView: android.widget.TextView? = null,
    note: String? = null,
) = FrameLayout(this).apply {
    setBackgroundColor(color(R.color.bg))
    val heading = titleView ?: label(title, sizeSp = 34f, bold = true)
    heading.text = title
    heading.gravity = Gravity.CENTER
    // Full-width text so a long title wraps instead of being cut off.
    val content = column(Gravity.CENTER_HORIZONTAL).apply {
        setPadding(dp(64), 0, dp(64), 0)
        addView(heading, android.widget.LinearLayout.LayoutParams(-1, -2))
        note?.let {
            val line = label(it, sizeSp = 18f, colorId = R.color.muted).apply { gravity = Gravity.CENTER }
            addView(line, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        addView(pad.also { it.margins(this@pinScreen, top = 36) })
    }
    addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
}
