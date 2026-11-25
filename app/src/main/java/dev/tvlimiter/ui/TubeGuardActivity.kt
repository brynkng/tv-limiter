package dev.tvlimiter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.domain.PairResult
import dev.tvlimiter.domain.PairingCodeEntry
import dev.tvlimiter.service.TrackingService
import dev.tvlimiter.service.TubeGuard
import java.util.concurrent.Executors

/**
 * "Connect to TubeGuard": the parent types the code from the TubeGuard parent app on an on-screen number
 * grid, with the remote's arrows and OK. The code is checked as soon as the last digit goes in. Behind the
 * passkey like the other settings screens.
 */
class TubeGuardActivity : GuardedActivity() {
    private val model = PairingViewModel()
    private val main = Handler(Looper.getMainLooper())
    private val boxes = ArrayList<TextView>()
    private lateinit var message: TextView
    private val keys = HashMap<String, View>()
    private var lastKey = "5"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TubeGuard.enabled) return finish()
        showEntry()
    }

    override fun onUnlockedResume() = Unit

    private fun showEntry() {
        boxes.clear()
        keys.clear()
        val content = scrollScreen()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(64), dp(24), dp(64), dp(16))
        content.addView(label("Connect to TubeGuard", sizeSp = 30f, bold = true).apply { gravity = Gravity.CENTER }.fullWidth(this))

        // One line of help, which gives way to what is happening or what went wrong.
        message = label("", sizeSp = 16f).apply { gravity = Gravity.CENTER }
        content.addView(message, LinearLayout.LayoutParams(dp(760), dp(56)).apply { topMargin = dp(6) })

        val code = row().apply { clipChildren = false }
        repeat(PairingCodeEntry.LENGTH) { i ->
            val box = label("", sizeSp = 26f, weight = Weight.MEDIUM).apply {
                gravity = Gravity.CENTER
                background = rounded(R.color.panel, radiusDp = 10)
            }
            boxes += box
            val gap = if (i == PairingCodeEntry.LENGTH / 2 - 1) 24 else 8
            code.addView(box, LinearLayout.LayoutParams(dp(44), dp(52)).apply { marginEnd = dp(gap) })
        }
        content.addView(code.margins(this, top = 8))
        content.addView(grid().margins(this, top = 8))
        render()
        // After the first layout, so the focus lands on the key in the middle of the grid.
        content.post { keys[lastKey]?.requestFocus() }
    }

    /** Three keys across: 1 to 9, then Delete, 0 and Cancel. */
    private fun grid(): LinearLayout {
        val grid = column(Gravity.CENTER_HORIZONTAL).apply { clipChildren = false }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Delete", "0", "Cancel"))
        for (line in rows) {
            val r = row().apply { clipChildren = false }
            for (name in line) {
                val key = tvButton(name) { press(name) }.apply {
                    // Keys are the same size so the grid reads as a grid from across the room.
                    minHeight = dp(44)
                    minimumHeight = dp(44)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                keys[name] = key
                r.addView(key, LinearLayout.LayoutParams(dp(140), dp(46)).apply { marginEnd = dp(8); topMargin = dp(8) })
            }
            grid.addView(r)
        }
        return grid
    }

    private fun press(name: String) {
        when (name) {
            "Cancel" -> return finish()
            "Delete" -> model.delete()
            else -> model.press(name.toInt())
        }
        lastKey = name
        val code = model.beginCheck()
        render()
        if (code != null) check(code)
    }

    private fun check(code: String) {
        val app = applicationContext
        worker.execute {
            val result = TubeGuard.pair(app, code)
            // Saved here, before the screen hears about it, so a screen closed meanwhile still leaves the TV paired.
            if (result is PairResult.Paired) TubeGuard.connected(app, result)
            main.post {
                if (isDestroyed) return@post
                model.finishCheck(result)
                if (model.status is PairingViewModel.Status.Connected) showConnected() else render()
            }
        }
    }

    private fun render() {
        if (model.status is PairingViewModel.Status.Connected) return
        boxes.forEachIndexed { i, box ->
            val digit = model.entry.digitAt(i)
            box.text = digit?.toString() ?: ""
            box.background = rounded(
                R.color.panel,
                radiusDp = 10,
                strokeColorId = if (model.status is PairingViewModel.Status.Problem) R.color.warn else if (digit != null) R.color.accent else null,
            )
        }
        val status = model.status
        message.setTextColor(color(if (status is PairingViewModel.Status.Problem) R.color.warn else R.color.muted))
        message.text = when (status) {
            PairingViewModel.Status.Checking -> "Checking the code…"
            is PairingViewModel.Status.Problem -> status.message
            else -> "In the TubeGuard parent app, choose your child to get an 8 digit code, then type it here."
        }
        // Focus stays on the grid whatever happened, so a retry never needs a hunt for the keys.
        if (currentFocus == null) keys[lastKey]?.requestFocus()
    }

    private fun showConnected() {
        val status = model.status as PairingViewModel.Status.Connected
        val root = FrameLayout(this).apply { setBackgroundColor(color(R.color.bg)) }
        setContentView(root)
        val content = column(Gravity.CENTER_HORIZONTAL).apply { setPadding(dp(64), 0, dp(64), 0) }
        content.addView(label("Connected to TubeGuard", sizeSp = 34f, bold = true).apply { gravity = Gravity.CENTER }.fullWidth(this))
        content.addView(
            label("This TV is now paired to ${PairingViewModel.names(status.children)}.", sizeSp = 22f).apply { gravity = Gravity.CENTER }
                .margins(this, top = 16),
        )
        val access = if (PermissionStatus.hasNotificationAccess(this)) {
            ""
        } else {
            " One more step: on the main screen, allow notification access so TV Limiter can see what is playing."
        }
        content.addView(
            label(
                "Each YouTube video watched here is now reported to your family's TubeGuard parent app. Nothing is reported while parent mode is on.$access",
                sizeSp = 16f,
                colorId = R.color.muted,
            ).apply { gravity = Gravity.CENTER }.margins(this, top = 12),
        )
        askToShowNotice()
        val done = tvButton("Done") { finish() }
        content.addView(done.margins(this, top = 28))
        root.addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        done.requestFocus()
    }

    /** Android 13 and newer needs this before the "monitored by TubeGuard" notice shows. */
    private fun askToShowNotice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTICE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTICE) TrackingService.refreshNotice(this)
    }

    /** A number key or Backspace on a keyboard or remote does the same as its button. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (model.status !is PairingViewModel.Status.Connected && event.repeatCount == 0) {
            when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> return true.also { press((keyCode - KeyEvent.KEYCODE_0).toString()) }
                KeyEvent.KEYCODE_DEL -> return true.also { press("Delete") }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val REQUEST_NOTICE = 43
        val worker = Executors.newSingleThreadExecutor()
    }
}
