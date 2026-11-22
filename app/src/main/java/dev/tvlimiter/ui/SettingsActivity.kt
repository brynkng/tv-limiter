package dev.tvlimiter.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.service.TrackingService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The owner's home screen: every limit with today's time left, a way to add app limits, and permission status. */
class SettingsActivity : GuardedActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackingService.start(this)
        content = scrollScreen()
    }

    override fun unlockIntent(): Intent =
        PinRepository.guard(this).let { guard ->
            // A damaged passkey file goes to the passkey gate, which explains it, never to setup.
            if (guard.hasPin() || guard.isDamaged()) super.unlockIntent() else Intent(this, PinSetupActivity::class.java)
        }

    override fun onUnlockedResume() = render()

    private fun render() {
        content.removeAllViews()
        val header = row()
        header.addView(label(getString(R.string.app_name), sizeSp = 34f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), sizeSp = 18f, colorId = R.color.muted))
        content.addView(header.fullWidth(this))
        content.addView(label("Your child's screen time today", sizeSp = 18f, colorId = R.color.muted).margins(this, top = 2))

        val usageRepo = UsageRepository.get(this)
        val usage = usageRepo.today()
        val day = usageRepo.weekday()
        val limits = LimitsRepository.get(this).all().sortedBy { !it.isWholeTv }
        var first: View? = null
        limits.forEachIndexed { index, limit ->
            val row = limitRow(limit, LimitSummary.of(limit, usage, day))
            content.addView(row.fullWidth(this, top = if (index == 0) 28 else 12))
            if (first == null) first = row
        }
        val buttons = row().apply { clipChildren = false }
        buttons.addView(tvButton("Add an app") { startActivity(Intent(this, AppPickerActivity::class.java)) }.margins(this, end = 12))
        buttons.addView(tvButton("See screen time history") { startActivity(Intent(this, ReportActivity::class.java)) })
        content.addView(buttons.margins(this, top = 24))
        passkeyRow()
        permissions()
        first?.requestFocus()
    }

    private fun limitRow(limit: Limit, summary: LimitSummary): View {
        val row = focusRow { openEditor(limit.id) }
        row.addView(label(limitName(limit), sizeSp = 22f, weight = Weight.MEDIUM), LinearLayout.LayoutParams(dp(240), -2))
        val bar = TimeBar(this).apply {
            fraction = summary.fraction
            exhausted = summary.exhausted
        }
        row.addView(bar, LinearLayout.LayoutParams(0, dp(12), 1f).apply { marginEnd = dp(28) })
        val status = column(Gravity.END)
        status.addView(
            label(summary.status, sizeSp = 18f, colorId = if (summary.exhausted) R.color.warn else R.color.text).apply { gravity = Gravity.END },
        )
        summary.added?.let {
            status.addView(label("Added $it", sizeSp = 15f, colorId = R.color.muted).apply { gravity = Gravity.END })
        }
        row.addView(status, LinearLayout.LayoutParams(dp(300), -2))
        return row
    }

    private fun passkeyRow() {
        val row = row()
        val text = column()
        text.addView(label("Parent passkey", sizeSp = 20f, weight = Weight.MEDIUM))
        text.addView(label("Needed to change limits or add time.", sizeSp = 15f, colorId = R.color.muted))
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(
            tvButton("Change passkey") {
                @Suppress("DEPRECATION")
                startActivityForResult(PinSetupActivity.changeIntent(this), REQUEST_CHANGE_PASSKEY)
            },
        )
        content.addView(row.fullWidth(this, top = 40))
    }

    @Deprecated("Framework activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CHANGE_PASSKEY) {
            if (resultCode == RESULT_OK) Toast.makeText(this, "Passkey changed", Toast.LENGTH_LONG).show()
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    /** One quiet line when everything is granted; otherwise a card that says what to fix. */
    private fun permissions() {
        val usage = PermissionStatus.hasUsageAccess(this)
        val overlay = PermissionStatus.canDrawOverlays(this)
        if (usage && overlay) {
            content.addView(label("Tracking is on. Limits apply to everyone using this TV.", sizeSp = 15f, colorId = R.color.muted).margins(this, top = 32))
            return
        }
        val card = column().apply {
            background = rounded(R.color.panel, radiusDp = 16, strokeColorId = R.color.warn)
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }
        card.addView(label("Your child's limits can't work until these are allowed", sizeSp = 20f, weight = Weight.MEDIUM, colorId = R.color.warn))
        if (!usage) {
            permissionRow(card, "Usage access", "Lets TV Limiter see which app is open.", PermissionStatus.usageAccessScreen(), PermissionStatus.USAGE_ADB)
        }
        if (!overlay) {
            permissionRow(
                card,
                "Display over other apps",
                "Lets TV Limiter cover an app when its time is up.",
                PermissionStatus.overlayScreen(this),
                PermissionStatus.OVERLAY_ADB,
            )
        }
        card.addView(
            label(
                "If a button doesn't open anything, this TV hides that screen. Run the adb command from a computer instead.",
                sizeSp = 14f,
                colorId = R.color.muted,
            ).margins(this, top = 16),
        )
        content.addView(card.fullWidth(this, top = 40))
    }

    private fun permissionRow(card: LinearLayout, name: String, why: String, screen: Intent, adb: String) {
        val row = row()
        val text = column()
        text.addView(label(name, sizeSp = 18f, weight = Weight.MEDIUM))
        text.addView(label(why, sizeSp = 15f, colorId = R.color.muted))
        text.addView(label(adb, sizeSp = 13f, colorId = R.color.muted).margins(this, top = 4))
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tvButton("Allow") { openSystemScreen(screen) })
        card.addView(row.fullWidth(this, top = 16))
    }

    private fun openSystemScreen(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "This TV doesn't have that screen. Use the adb command.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditor(limitId: String) = startActivity(ScheduleEditorActivity.intent(this, limitId))

    private companion object {
        const val REQUEST_CHANGE_PASSKEY = 42
    }
}
