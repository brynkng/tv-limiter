package dev.tvlimiter.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import dev.tvlimiter.R
import dev.tvlimiter.domain.Arrow
import dev.tvlimiter.domain.PasskeyState
import dev.tvlimiter.domain.PinCheck
import java.util.concurrent.Executors

/**
 * Passkey entry. The code is a sequence of arrow presses on the remote, and the screen shows
 * only a row of circles that fill in as presses are entered. Every arrow fills the next circle
 * the same way, so the screen never shows which arrow was pressed. Held keys count once, and
 * number keys and OK are swallowed.
 *
 * Checking a code hashes it, which is slow on a TV, so the check runs on a background thread.
 * Meanwhile all circles stay filled and the hint says it is checking; keys are ignored.
 */
class PinPadView(context: Context) : LinearLayout(context) {
    private val state = PasskeyState()
    private val circles = context.row()
    private val hint = context.label(HINT, sizeSp = 16f, colorId = R.color.muted)
    private val feedback = context.label("", sizeSp = 16f, colorId = R.color.warn)
    private var lockoutSeconds: () -> Int = { 0 }
    private var onSubmit: (String) -> (() -> Unit) = { {} }
    private var checking = false

    /** After a check, the circles stay full until the screen moves on or shows a wrong code. */
    private var holding = false

    /** True while a wrong code shakes the circles, before they clear. */
    private var wrong = false
    private val pulse = ObjectAnimator.ofFloat(circles, ALPHA, 1f, 0.4f).apply {
        duration = 450
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }
    private val startPulse = Runnable { pulse.start() }

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
        // The pad takes keys without showing a focus box; the circles are its only feedback.
        defaultFocusHighlightEnabled = false
        repeat(PasskeyState.LENGTH) {
            val size = context.dp(CIRCLE_DP)
            circles.addView(View(context), LayoutParams(size, size).apply { marginEnd = context.dp(16) })
        }
        addView(circles)
        addView(hint.also { it.margins(context, top = 24) })
        addView(feedback.also { it.margins(context, top = 16) })
        render()
    }

    /**
     * [onSubmit] runs on a background thread with the finished code and returns what to do
     * with the result, which then runs on the main thread.
     */
    fun bind(lockoutSeconds: () -> Int, onSubmit: (String) -> (() -> Unit)) {
        this.lockoutSeconds = lockoutSeconds
        this.onSubmit = onSubmit
        open()
    }

    /**
     * Starts a fresh entry, as each time the pad is opened. With a [message], the entry was
     * refused (setup codes that didn't match), so the circles shake first.
     */
    fun open(message: String? = null) {
        requestFocus()
        if (message != null) {
            feedback.text = message
            shakeThenClear()
            return
        }
        state.clear()
        holding = false
        feedback.text = ""
        removeCallbacks(countdown)
        if (lockoutSeconds() > 0) post(countdown)
        render()
    }

    /** Shows the outcome of a submitted code. On success the circles stay full while the screen moves on. */
    fun showResult(check: PinCheck) {
        when (check) {
            PinCheck.Ok -> return
            is PinCheck.LockedOut -> {
                removeCallbacks(countdown)
                post(countdown)
            }
            else -> feedback.text = pinFeedback(check)
        }
        shakeThenClear()
    }

    /** Turns the circles pink and shakes them side to side, then empties them for the next try. */
    private fun shakeThenClear() {
        wrong = true
        holding = false
        state.clear()
        render()
        val d = context.dp(1).toFloat()
        ObjectAnimator.ofFloat(circles, TRANSLATION_X, 0f, -14 * d, 14 * d, -10 * d, 10 * d, -5 * d, 5 * d, 0f).apply {
            duration = SHAKE_MILLIS
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    circles.translationX = 0f
                    wrong = false
                    render()
                }
            })
            start()
        }
    }

    /** Back clears a partial entry first. Returns false when there was nothing to clear. */
    fun handleBack(): Boolean {
        if (checking || wrong) return true
        if (state.length == 0) return false
        state.clear()
        render()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val arrow = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Arrow.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Arrow.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Arrow.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Arrow.RIGHT
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> return true
            else -> return super.onKeyDown(keyCode, event)
        }
        if (event.repeatCount > 0 || checking || wrong || lockoutSeconds() > 0) return true
        holding = false
        val code = state.press(arrow)
        if (code != null) submit(code) else render()
        return true
    }

    private fun submit(code: String) {
        checking = true
        feedback.text = ""
        render()
        // A quick check finishes before the pulse would start, so nothing flickers.
        postDelayed(startPulse, PULSE_DELAY_MILLIS)
        worker.execute {
            val started = SystemClock.elapsedRealtime()
            val show = onSubmit(code)
            Log.i(TAG, "Passkey check took ${SystemClock.elapsedRealtime() - started} ms")
            main.post {
                // Keep the circles full; the result decides what comes next (a new screen or a shake).
                checking = false
                holding = true
                removeCallbacks(startPulse)
                pulse.cancel()
                circles.alpha = 1f
                render()
                show()
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(countdown)
        removeCallbacks(startPulse)
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    private fun render() {
        val locked = lockoutSeconds() > 0
        hint.text = if (checking) "Checking…" else HINT
        val full = checking || holding || wrong
        val color = if (wrong) R.color.warn else R.color.accent
        for (i in 0 until PasskeyState.LENGTH) {
            circles.getChildAt(i).background = context.rounded(
                colorId = if (full || i < state.length) color else R.color.panel,
                radiusDp = CIRCLE_DP / 2,
                strokeColorId = if (locked && !wrong) R.color.muted else color,
            )
        }
    }

    private companion object {
        const val CIRCLE_DP = 28
        const val PULSE_DELAY_MILLIS = 150L
        const val SHAKE_MILLIS = 420L
        const val TAG = "PinPadView"
        val HINT = "Press ${PasskeyState.LENGTH} arrow buttons on the remote"
        val main = Handler(Looper.getMainLooper())

        /** One thread for every pad, so two checks never race on the passkey file. */
        val worker = Executors.newSingleThreadExecutor()
    }
}
