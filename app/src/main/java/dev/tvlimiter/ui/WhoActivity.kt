package dev.tvlimiter.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.ProfilesRepository
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.Viewer

/**
 * The "Who's watching" screen, in profiles mode. It covers the TV when it wakes and covers any
 * app opened before someone picks. Each child picks their tile, the parent can start parent
 * mode, and Settings leads to the parent's screens. Back goes home, never to the covered app.
 */
class WhoActivity : Activity() {
    private lateinit var model: WhoViewModel
    private var coveredPackage: String? = null
    private lateinit var title: TextView
    private lateinit var tiles: LinearLayout
    private lateinit var pad: PinPadView
    private var firstTile: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The service may ask again before this screen is up; keep any passkey entry in progress.
        intent.getStringExtra(EXTRA_PACKAGE)?.let { coveredPackage = it }
        if (model.step == WhoStep.CHOOSE) start(intent)
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        super.onStop()
        visible = false
        // Pressing Home leaves the picker behind; the next app opened brings it back.
        if (!isChangingConfigurations) finish()
    }

    @Deprecated("Back is handled here to keep the covered app from resuming")
    override fun onBackPressed() {
        when (model.step) {
            WhoStep.PIN -> if (!pad.handleBack()) {
                model.cancelPin()
                render()
            }
            else -> goHome()
        }
    }

    private fun start(intent: Intent) {
        coveredPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: coveredPackage
        val profiles = ProfilesRepository.get(this).profiles()
        model = WhoViewModel(
            profiles = profiles,
            hasPasskey = { PinRepository.guard(this, it).hasPin() },
            childGuard = { PinRepository.guard(this, it) },
            parentGuard = PinRepository.guard(this),
            startViewer = Viewer.shared::start,
            startParentMode = ParentMode.shared::start,
        )
        fillTiles()
        render()
    }

    private fun render() {
        if (model.step == WhoStep.DONE) return done()
        title.text = model.title
        tiles.visibility = if (model.step == WhoStep.CHOOSE) View.VISIBLE else View.GONE
        pad.visibility = if (model.step == WhoStep.PIN) View.VISIBLE else View.GONE
        when (model.step) {
            WhoStep.CHOOSE -> firstTile?.requestFocus()
            WhoStep.PIN -> pad.open()
            WhoStep.DONE -> Unit
        }
    }

    private fun buildViews() {
        title = label("", sizeSp = 40f, bold = true).apply { gravity = Gravity.CENTER }
        tiles = column(Gravity.CENTER_HORIZONTAL).apply { clipChildren = false }
        pad = PinPadView(this)
        // Binding opens the pad once, before the model exists.
        pad.bind(lockoutSeconds = { if (::model.isInitialized) model.lockoutSecondsLeft() else 0 }) { pin ->
            val check = model.checkPin(pin)
            val show = {
                model.applyCheck(check)
                if (check == PinCheck.Ok) render() else pad.showResult(check)
            }
            show
        }
        val content = column(Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dp(64), dp(48), dp(64), dp(48))
            clipChildren = false
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(tiles.also { it.margins(this@WhoActivity, top = 44) })
            addView(pad.also { it.margins(this@WhoActivity, top = 44) })
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(color(R.color.bg))
                clipChildren = false
                addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            },
        )
    }

    /** One tile per child, then the Parent tile, in rows of four, with Settings below. */
    private fun fillTiles() {
        tiles.removeAllViews()
        val all = model.profiles.map { profile ->
            tile(profile.name, profile.band.label) { pick(WhoChoice.Child(profile)) }
        } + tile("Parent", "Turns off limits until the TV sleeps") { pick(WhoChoice.Parent) }
        all.chunked(COLUMNS).forEachIndexed { index, chunk ->
            val line = row().apply {
                gravity = Gravity.CENTER
                clipChildren = false
            }
            chunk.forEachIndexed { i, tile ->
                // Full height of the row, so a tile with a longer note doesn't stand taller than the rest.
                line.addView(tile, LinearLayout.LayoutParams(dp(TILE_WIDTH), -1).apply { if (i < chunk.lastIndex) marginEnd = dp(20) })
            }
            tiles.addView(line.margins(this, top = if (index == 0) 0 else 20))
        }
        firstTile = all.firstOrNull()
        tiles.addView(tvButton("Settings") { openSettings() }.margins(this, top = 36))
    }

    private fun tile(name: String, note: String, onPick: () -> Unit): View = column(Gravity.CENTER_HORIZONTAL).apply {
        isFocusable = true
        isClickable = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.panel_raised, radiusDp = 16, strokeColorId = R.color.accent))
            addState(intArrayOf(), rounded(R.color.panel, radiusDp = 16))
        }
        setPadding(dp(16), dp(28), dp(16), dp(24))
        growOnFocus()
        setOnClickListener { onPick() }
        addView(
            label(name, sizeSp = 26f, weight = Weight.SEMIBOLD).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        addView(
            label(note, sizeSp = 15f, colorId = R.color.muted).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) },
        )
    }

    private fun pick(choice: WhoChoice) {
        model.pick(choice)
        render()
    }

    private fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_FROM_PICKER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    /**
     * Someone picked: go back to the app that was covered, or home when there was none. Going
     * home also keeps a settings screen left open underneath from coming up and asking for the
     * parent passkey.
     */
    private fun done() {
        val pkg = coveredPackage
        val launch = pkg?.let { packageManager.getLeanbackLaunchIntentForPackage(it) ?: packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } else {
            goHome()
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val COLUMNS = 4
        private const val TILE_WIDTH = 220

        /** Whether the picker is on screen now; read by the tracking service thread. */
        @Volatile var visible = false
            private set

        /** Opens the picker, over [coveredPackage] when an app was opened before anyone picked. */
        fun intent(context: Context, coveredPackage: String? = null): Intent =
            Intent(context, WhoActivity::class.java)
                .apply { coveredPackage?.let { putExtra(EXTRA_PACKAGE, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
