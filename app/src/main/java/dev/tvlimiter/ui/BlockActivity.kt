package dev.tvlimiter.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.GrantChoice
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.Profiles

/**
 * Covers an app whose time ran out. The viewer can only go home or hand the remote to the
 * parent for the passkey. Leaving this screen never resumes the blocked app.
 */
class BlockActivity : Activity() {
    private lateinit var model: BlockViewModel
    private lateinit var blockedPackage: String
    private lateinit var profileId: String
    private lateinit var headline: TextView
    private lateinit var subline: TextView
    private lateinit var choosePanel: LinearLayout
    private lateinit var pad: PinPadView
    private lateinit var grantPanel: LinearLayout
    private lateinit var grantTitle: TextView
    private lateinit var grantChoices: LinearLayout
    private lateinit var homeButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The service may ask again for the same app before this screen is up; keep any PIN entry in progress.
        if (::blockedPackage.isInitialized && intent.getStringExtra(EXTRA_PACKAGE) == blockedPackage && profileOf(intent) == profileId) return
        start(intent)
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        super.onStop()
        visible = false
        // Pressing Home leaves the app behind; the next attempt to open it blocks again.
        if (!isChangingConfigurations) finish()
    }

    @Deprecated("Back is handled here to keep the blocked app from resuming")
    override fun onBackPressed() {
        when (model.step) {
            BlockStep.PIN -> if (!pad.handleBack()) {
                model.cancelPin()
                render()
            }
            else -> goHome()
        }
    }

    private fun start(intent: Intent) {
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        // Limits, usage and any time added all belong to whoever is watching.
        profileId = profileOf(intent)
        val usage = UsageRepository.get(this, profileId)
        val limits = LimitsRepository.get(this, profileId)
        model = BlockViewModel(
            blockedPackage = blockedPackage,
            limits = limits::all,
            usage = usage::today,
            weekday = usage::weekday,
            recordGrant = usage::grant,
            guard = PinRepository.guard(this),
            appLabel = { appLabel(packageManager, it) },
            startParentMode = ParentMode.shared::start,
        )
        render()
    }

    private fun render() {
        if (model.step == BlockStep.DONE) return relaunch()
        headline.text = model.headline
        choosePanel.visibility = if (model.step == BlockStep.CHOOSE) View.VISIBLE else View.GONE
        pad.visibility = if (model.step == BlockStep.PIN) View.VISIBLE else View.GONE
        grantPanel.visibility = if (model.step == BlockStep.GRANT) View.VISIBLE else View.GONE
        when (model.step) {
            BlockStep.CHOOSE -> {
                subline.text = "Time for something else. If you need more, ask a parent."
                homeButton.requestFocus()
            }
            BlockStep.PIN -> subline.text = "Parent: enter your passkey."
            BlockStep.GRANT -> {
                subline.text = "Extra time is for today only."
                grantTitle.text = "Add time to ${model.limitName}"
                grantChoices.getChildAt(0)?.requestFocus()
            }
            BlockStep.DONE -> Unit
        }
    }

    private fun buildViews() {
        headline = label("", sizeSp = 42f, bold = true).apply { gravity = Gravity.CENTER }
        subline = label("", sizeSp = 20f, colorId = R.color.muted).apply { gravity = Gravity.CENTER }
        // An empty pink bar: the same bar the parent sees on the home screen, run out.
        val spent = TimeBar(this).apply { exhausted = true }

        choosePanel = row().apply { gravity = Gravity.CENTER }
        homeButton = tvButton("Go home") { goHome() }
        choosePanel.addView(homeButton.margins(this, end = 20))
        choosePanel.addView(
            tvButton("Ask a parent") {
                model.requestPin()
                render()
                pad.open()
            },
        )

        pad = PinPadView(this)
        pad.bind(lockoutSeconds = { PinRepository.guard(this).lockoutSecondsLeft() }) { pin ->
            val check = model.checkPin(pin)
            val show = {
                model.applyCheck(check)
                if (check == PinCheck.Ok) render() else pad.showResult(check)
            }
            show
        }

        grantTitle = label("", sizeSp = 24f, weight = Weight.MEDIUM).apply { gravity = Gravity.CENTER }
        val choices = row().apply {
            gravity = Gravity.CENTER
            clipChildren = false
        }
        grantChoices = choices
        GrantChoice.entries.forEach { choice ->
            choices.addView(
                tvButton(choice.label) {
                    model.grant(choice)
                    render()
                }.margins(this, end = 16),
            )
        }
        // On its own line: it isn't more time for this limit but no limits at all until the TV sleeps.
        val parentMode = tvButton("Parent mode until the TV sleeps") {
            model.parentMode()
            render()
        }
        grantPanel = column(Gravity.CENTER_HORIZONTAL).apply {
            clipChildren = false
            addView(grantTitle)
            addView(choices.also { it.margins(this@BlockActivity, top = 20) })
            addView(parentMode.margins(this@BlockActivity, top = 16))
        }

        val content = column(Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dp(64), dp(48), dp(64), dp(48))
            clipChildren = false
            addView(headline, LinearLayout.LayoutParams(-1, -2))
            addView(spent, LinearLayout.LayoutParams(dp(420), dp(12)).apply { topMargin = dp(28) })
            addView(subline, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
            addView(choosePanel.also { it.margins(this@BlockActivity, top = 44) })
            addView(pad.also { it.margins(this@BlockActivity, top = 44) })
            addView(grantPanel.also { it.margins(this@BlockActivity, top = 44) })
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(color(R.color.bg))
                clipChildren = false
                addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            },
        )
    }

    private fun relaunch() {
        val launch = packageManager.getLeanbackLaunchIntentForPackage(blockedPackage)
            ?: packageManager.getLaunchIntentForPackage(blockedPackage)
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else goHome()
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_PROFILE = "profile"

        /** Whether the block screen is on screen now; read by the tracking service thread. */
        @Volatile var visible = false
            private set

        fun intent(context: Context, packageName: String, profileId: String = Profiles.DEFAULT_ID): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_PROFILE, profileId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        private fun profileOf(intent: Intent): String = intent.getStringExtra(EXTRA_PROFILE) ?: Profiles.DEFAULT_ID

        fun appLabel(pm: PackageManager, packageName: String): String = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
