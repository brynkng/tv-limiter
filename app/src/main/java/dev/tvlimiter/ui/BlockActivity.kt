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
import dev.tvlimiter.domain.PinCheck

/**
 * Covers an app whose time ran out. The viewer can only go home or hand the remote to the
 * owner for the PIN. Leaving this screen never resumes the blocked app.
 */
class BlockActivity : Activity() {
    private lateinit var model: BlockViewModel
    private lateinit var blockedPackage: String
    private lateinit var headline: TextView
    private lateinit var subline: TextView
    private lateinit var choosePanel: LinearLayout
    private lateinit var pad: PinPadView
    private lateinit var grantPanel: LinearLayout
    private lateinit var grantTitle: TextView
    private lateinit var homeButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        start(intent)
    }

    override fun onStop() {
        super.onStop()
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
        val usage = UsageRepository.get(this)
        val limits = LimitsRepository.get(this)
        model = BlockViewModel(
            blockedPackage = blockedPackage,
            limits = limits::all,
            usage = usage::today,
            weekday = usage::weekday,
            recordGrant = usage::grant,
            guard = PinRepository.guard(this),
            appLabel = { appLabel(packageManager, it) },
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
                subline.text = "Ask the owner for more time, or go home."
                homeButton.requestFocus()
            }
            BlockStep.PIN -> subline.text = "Owner: enter the PIN where the viewer can't see the screen."
            BlockStep.GRANT -> {
                subline.text = "Extra time applies to today only."
                grantTitle.text = "Add time to ${model.limitName}"
                grantPanel.getChildAt(1)?.let { (it as LinearLayout).getChildAt(0)?.requestFocus() }
            }
            BlockStep.DONE -> Unit
        }
    }

    private fun buildViews() {
        headline = label("", sizeSp = 40f, bold = true).apply { gravity = Gravity.CENTER }
        subline = label("", sizeSp = 20f, colorId = R.color.muted).apply { gravity = Gravity.CENTER }

        choosePanel = row().apply { gravity = Gravity.CENTER }
        homeButton = tvButton("Go home") { goHome() }
        choosePanel.addView(homeButton.margins(this, end = 16))
        choosePanel.addView(
            tvButton("Enter PIN") {
                model.requestPin()
                render()
                pad.open()
            },
        )

        pad = PinPadView(this)
        pad.bind(lockoutSeconds = { PinRepository.guard(this).lockoutSecondsLeft() }) { pin ->
            val check = model.submitPin(pin)
            if (check == PinCheck.Ok) render() else pad.showResult(check)
        }

        grantTitle = label("", sizeSp = 22f, bold = true).apply { gravity = Gravity.CENTER }
        val choices = row().apply { gravity = Gravity.CENTER }
        GrantChoice.entries.forEach { choice ->
            choices.addView(
                tvButton(choice.label) {
                    model.grant(choice)
                    render()
                }.margins(this, end = 12),
            )
        }
        grantPanel = column(Gravity.CENTER_HORIZONTAL).apply {
            addView(grantTitle)
            addView(choices.also { it.margins(this@BlockActivity, top = 16) })
        }

        val content = column(Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dp(48), dp(48), dp(48), dp(48))
            addView(headline)
            addView(subline.also { it.margins(this@BlockActivity, top = 12) })
            addView(choosePanel.also { it.margins(this@BlockActivity, top = 40) })
            addView(pad.also { it.margins(this@BlockActivity, top = 40) })
            addView(grantPanel.also { it.margins(this@BlockActivity, top = 40) })
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(color(R.color.bg))
                addView(content, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
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

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun appLabel(pm: PackageManager, packageName: String): String = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
