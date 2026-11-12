package dev.tvlimiter.ui

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.ScrambledPadState

/**
 * The masked PIN pad. Left and right move along a shuffled row of digits plus Delete and
 * Done, and OK picks the cell under the cursor. The field above shows only arrows. Number keys
 * on the remote are swallowed so nothing typed directly can reach the field.
 */
class PinPadView(context: Context) : LinearLayout(context) {
    private var state = ScrambledPadState()
    private val field = context.label("", sizeSp = 34f, bold = true)
    private val cells = context.row()
    private val feedback = context.label("", sizeSp = 16f, colorId = R.color.warn)
    private var lockoutSeconds: () -> Int = { 0 }
    private var onSubmit: (String) -> Unit = {}

    private val countdown = object : Runnable {
        override fun run() {
            val left = lockoutSeconds()
            if (left > 0) {
                feedback.text = lockoutMessage(left)
                postDelayed(this, 1_000)
            } else {
                feedback.text = ""
            }
            render()
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isFocusable = true
        isFocusableInTouchMode = true
        field.gravity = Gravity.CENTER
        field.minHeight = context.dp(56)
        addView(field)
        addView(cells.also { it.margins(context, top = 16) })
        addView(feedback.also { it.margins(context, top = 16) })
        render()
    }

    fun bind(lockoutSeconds: () -> Int, onSubmit: (String) -> Unit) {
        this.lockoutSeconds = lockoutSeconds
        this.onSubmit = onSubmit
        open()
    }

    /** Starts a fresh entry with a new digit order, as each time the pad is opened. */
    fun open(message: String? = null) {
        state = ScrambledPadState()
        feedback.text = message ?: ""
        removeCallbacks(countdown)
        if (lockoutSeconds() > 0) post(countdown)
        render()
        requestFocus()
    }

    /** Shows the outcome of a submitted PIN. Nothing to show on success. */
    fun showResult(check: PinCheck) {
        when (check) {
            PinCheck.Ok -> return
            is PinCheck.LockedOut -> {
                state.reset()
                removeCallbacks(countdown)
                post(countdown)
            }
            else -> {
                state.reset()
                feedback.text = pinFeedback(check)
            }
        }
        render()
    }

    /** Back clears a partial entry first. Returns false when there was nothing to clear. */
    fun handleBack(): Boolean {
        if (state.length == 0) return false
        state.clear()
        render()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return true
        if (lockoutSeconds() > 0) return keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_HOME
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> state.left()
            KeyEvent.KEYCODE_DPAD_RIGHT -> state.right()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val pin = state.ok()
                render()
                if (pin != null) onSubmit(pin)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
            else -> return super.onKeyDown(keyCode, event)
        }
        render()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(countdown)
        super.onDetachedFromWindow()
    }

    private fun render() {
        val locked = lockoutSeconds() > 0
        field.text = state.display.ifEmpty { "Enter PIN" }
        field.setTextColor(context.color(if (state.length == 0) R.color.muted else R.color.text))
        if (cells.childCount != ScrambledPadState.CELL_COUNT) {
            cells.removeAllViews()
            repeat(ScrambledPadState.CELL_COUNT) {
                cells.addView(
                    TextView(context).apply {
                        gravity = Gravity.CENTER
                        textSize = 22f
                        minWidth = context.dp(52)
                        minHeight = context.dp(52)
                        setPadding(context.dp(10), 0, context.dp(10), 0)
                    }.also { it.margins(context, end = 6) },
                )
            }
        }
        for (i in 0 until ScrambledPadState.CELL_COUNT) {
            val cell = cells.getChildAt(i) as TextView
            val focused = i == state.cursor && !locked
            val dimmed = locked || (i == ScrambledPadState.DONE && !state.canSubmit)
            cell.text = state.label(i)
            cell.setTextColor(context.color(if (dimmed) R.color.muted else R.color.text))
            cell.background = if (focused) {
                context.rounded(R.color.panel, strokeColorId = R.color.accent)
            } else {
                context.rounded(R.color.panel)
            }
        }
    }
}
