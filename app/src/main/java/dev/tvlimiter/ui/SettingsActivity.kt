package dev.tvlimiter.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.LimitEvaluator
import dev.tvlimiter.service.TrackingService

/** The owner's home screen: every limit with today's use, a way to add app limits, and permission status. */
class SettingsActivity : GuardedActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackingService.start(this)
        content = scrollScreen()
    }

    override fun unlockIntent(): Intent =
        PinRepository.guard(this).let { guard ->
            // A damaged PIN file goes to the PIN gate, which explains it, never to setup.
            if (guard.hasPin() || guard.isDamaged()) super.unlockIntent() else Intent(this, PinSetupActivity::class.java)
        }

    override fun onUnlockedResume() = render()

    private fun render() {
        content.removeAllViews()
        content.addView(label(getString(R.string.app_name), sizeSp = 32f, bold = true))

        section("Limits")
        val limits = LimitsRepository.get(this).all().sortedBy { !it.isWholeTv }
        var first: View? = null
        limits.forEach { limit ->
            val button = tvButton("${limitName(limit)}: ${limitStatus(limit)}") { openEditor(limit.id) }
            content.addView(button.margins(this, top = 10))
            if (first == null) first = button
        }
        content.addView(
            tvButton("Add app limit") { startActivity(Intent(this, AppPickerActivity::class.java)) }
                .margins(this, top = 16),
        )

        section("Permissions")
        permissionRow(
            "Usage access",
            PermissionStatus.hasUsageAccess(this),
            PermissionStatus.usageAccessScreen(),
            PermissionStatus.USAGE_ADB,
        )
        permissionRow(
            "Display over other apps",
            PermissionStatus.canDrawOverlays(this),
            PermissionStatus.overlayScreen(this),
            PermissionStatus.OVERLAY_ADB,
        )
        content.addView(
            label(
                "If a button above doesn't open anything, this TV hides that screen. Run the adb command shown instead.",
                sizeSp = 14f,
                colorId = R.color.muted,
            ).margins(this, top = 16),
        )
        first?.requestFocus()
    }

    private fun section(title: String) {
        content.addView(label(title, sizeSp = 22f, bold = true, colorId = R.color.accent).margins(this, top = 32))
    }

    private fun permissionRow(name: String, granted: Boolean, screen: Intent, adb: String) {
        val row = row()
        row.addView(
            label("$name: ${if (granted) "granted" else "not granted"}", colorId = if (granted) R.color.text else R.color.warn)
                .margins(this, end = 16),
        )
        row.addView(tvButton("Open settings") { openSystemScreen(screen) })
        content.addView(row.margins(this, top = 12))
        content.addView(label(adb, sizeSp = 14f, colorId = R.color.muted).margins(this, top = 4))
    }

    private fun openSystemScreen(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "This TV doesn't have that screen. Use the adb command.", Toast.LENGTH_LONG).show()
        }
    }

    private fun limitStatus(limit: Limit): String {
        val usageRepo = UsageRepository.get(this)
        val usage = usageRepo.today()
        val used = "used ${formatMinutes(usage.usedSeconds(limit))}"
        if (!limit.enabled) return "off, $used"
        if (limit.minutesOn(usageRepo.weekday()) == null) return "no limit today, $used"
        val left = LimitEvaluator.remainingSeconds(limit, usage, usageRepo.weekday())
        return if (left == null) "$used, unlimited for the rest of today" else "$used, ${formatMinutes(left)} left"
    }

    private fun openEditor(limitId: String) = startActivity(ScheduleEditorActivity.intent(this, limitId))
}
