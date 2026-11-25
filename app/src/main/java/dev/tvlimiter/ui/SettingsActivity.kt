package dev.tvlimiter.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvlimiter.R
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.ProfilesRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.Mode
import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.Profiles
import dev.tvlimiter.domain.Viewer
import dev.tvlimiter.domain.NotificationAccessGuide
import dev.tvlimiter.service.TrackingService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The owner's home screen: every limit with today's time left, a way to add app limits, the
 * profiles, and permission status. In profiles mode it shows one child's limits at a time.
 */
class SettingsActivity : GuardedActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackingService.start(this)
        // In profiles mode the TV Limiter icon opens the picker, which has its own way here.
        if (ProfilesRepository.get(this).mode() == Mode.PROFILES && !intent.getBooleanExtra(EXTRA_FROM_PICKER, false)) {
            startActivity(WhoActivity.intent(this))
            finish()
            return
        }
        content = scrollScreen()
    }

    override fun unlockIntent(): Intent =
        PinRepository.guard(this).let { guard ->
            // A damaged passkey file goes to the passkey gate, which explains it, never to setup.
            if (guard.hasPin() || guard.isDamaged()) super.unlockIntent() else Intent(this, PinSetupActivity::class.java)
        }

    override fun onUnlockedResume() = render()

    /** Rebuilds the screen. [focusTag] names the view to focus afterwards, so a button press doesn't lose its place. */
    private fun render(focusTag: String? = null) {
        content.removeAllViews()
        val profiles = ProfilesRepository.get(this).current()
        val shown = shownProfile(profiles.mode, profiles.profiles)
        val profileId = shown?.id ?: Profiles.SHARED_ID
        val header = row()
        header.addView(label(getString(R.string.app_name), sizeSp = 34f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), sizeSp = 18f, colorId = R.color.muted))
        content.addView(header.fullWidth(this))
        val whose = shown?.let { "${it.name}'s" } ?: "Your child's"
        content.addView(label("$whose screen time today", sizeSp = 18f, colorId = R.color.muted).margins(this, top = 2))
        if (profiles.mode == Mode.PROFILES) profileChooser(profiles.profiles, shown)

        val usageRepo = UsageRepository.get(this, profileId)
        val usage = usageRepo.today()
        val day = usageRepo.weekday()
        val limits = LimitsRepository.get(this, profileId).all().sortedBy { !it.isWholeTv }
        var first: View? = null
        limits.forEachIndexed { index, limit ->
            val row = limitRow(limit, LimitSummary.of(limit, usage, day), profileId)
            content.addView(row.fullWidth(this, top = if (index == 0) 28 else 12))
            if (first == null) first = row
        }
        val buttons = row().apply { clipChildren = false }
        buttons.addView(tvButton("Add an app") { startActivity(Intent(this, AppPickerActivity::class.java).withProfile(profileId)) }.margins(this, end = 12))
        buttons.addView(tvButton("See screen time history") { startActivity(Intent(this, ReportActivity::class.java).withProfile(profileId)) })
        content.addView(buttons.margins(this, top = 24))
        parentModeRow()
        profilesSection(profiles.mode, profiles.profiles)
        passkeyRow()
        permissions(profiles.mode)
        notificationAccess()
        Donate.addTo(this, content)
        (focusTag?.let { content.findViewWithTag<View>(it) } ?: first)?.requestFocus()
    }

    /** The child whose limits are shown: null in shared mode, where the shared limits are shown. */
    private fun shownProfile(mode: Mode, profiles: List<Profile>): Profile? {
        if (mode != Mode.PROFILES) return null
        return profiles.firstOrNull { it.id == shownProfileId } ?: profiles.firstOrNull()
    }

    /** One button per child; the limits below are for the one with the check mark. */
    private fun profileChooser(profiles: List<Profile>, shown: Profile?) {
        val line = row().apply { clipChildren = false }
        line.addView(label("Limits for", sizeSp = 18f, colorId = R.color.muted).margins(this, end = 16))
        profiles.forEach { profile ->
            val text = if (profile.id == shown?.id) "✓ ${profile.name}" else profile.name
            val button = tvButton(text) {
                shownProfileId = profile.id
                render(focusTag = "show:${profile.id}")
            }.apply { tag = "show:${profile.id}" }
            line.addView(button.margins(this, end = 12))
        }
        content.addView(line.margins(this, top = 20))
    }

    private fun limitRow(limit: Limit, summary: LimitSummary, profileId: String): View {
        val row = focusRow { openEditor(limit.id, profileId) }
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

    /** Parent mode can also start here, and this is where a parent ends it early. */
    private fun parentModeRow() {
        val on = ParentMode.shared.active
        val row = row()
        val text = column()
        text.addView(label(if (on) "Parent mode is on" else "Parent mode", sizeSp = 20f, weight = Weight.MEDIUM, colorId = if (on) R.color.accent else R.color.text))
        text.addView(
            label(
                if (on) "Nothing is counted or blocked until the screensaver starts or the TV goes to sleep." else "Turn off every limit until the screensaver starts or the TV goes to sleep.",
                sizeSp = 15f,
                colorId = R.color.muted,
            ),
        )
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val button = tvButton(if (on) "Turn off parent mode" else "Start parent mode") {
            if (on) ParentMode.shared.end() else ParentMode.shared.start()
            render(focusTag = TAG_PARENT_MODE)
        }.apply { tag = TAG_PARENT_MODE }
        row.addView(button)
        content.addView(row.fullWidth(this, top = 40))
    }

    /** The switch between shared limits and profiles, each child's profile, and a way to add one. */
    private fun profilesSection(mode: Mode, profiles: List<Profile>) {
        val on = mode == Mode.PROFILES
        val row = row()
        val text = column()
        text.addView(label(if (on) "Profiles are on" else "Profiles", sizeSp = 20f, weight = Weight.MEDIUM, colorId = if (on) R.color.accent else R.color.text))
        text.addView(
            label(
                if (on) {
                    "Each child picks their name when the TV wakes up. Their time counts only against their own limits."
                } else {
                    "Everyone shares the limits above. With profiles on, each child picks their name when the TV wakes up and has their own limits."
                },
                sizeSp = 15f,
                colorId = R.color.muted,
            ),
        )
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(24) })
        val switch = tvButton(if (on) "Go back to shared limits" else "Turn on profiles") { switchMode(turnOn = !on) }.apply { tag = TAG_MODE }
        row.addView(switch)
        content.addView(row.fullWidth(this, top = 40))

        profiles.forEach { profile ->
            val hasPasskey = PinRepository.guard(this, profile.id).hasPin()
            val item = focusRow { startActivity(ProfileEditorActivity.intent(this, profile.id)) }
            item.addView(label(profile.name, sizeSp = 20f, weight = Weight.MEDIUM), LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(label(profile.band.label, sizeSp = 16f, colorId = R.color.muted), LinearLayout.LayoutParams(dp(240), -2))
            val needs = profile.needsPasskey(hasPasskey)
            val status = when {
                needs -> "Needs a passkey"
                hasPasskey -> "Passkey set"
                else -> "No passkey needed"
            }
            item.addView(
                label(status, sizeSp = 16f, colorId = if (needs) R.color.warn else R.color.muted).apply { gravity = Gravity.END },
                LinearLayout.LayoutParams(dp(220), -2),
            )
            content.addView(item.fullWidth(this, top = 12))
        }
        val add = tvButton("Add a profile") { startActivity(ProfileEditorActivity.intent(this, null)) }.apply { id = View.generateViewId() }
        // With no profile rows between them, focus search from the right-hand switch skips this
        // left-hand button, so point the two at each other.
        switch.id = View.generateViewId()
        if (profiles.isEmpty()) {
            switch.nextFocusDownId = add.id
            add.nextFocusUpId = switch.id
        }
        content.addView(add.margins(this, top = 16))
    }

    private fun switchMode(turnOn: Boolean) {
        val repo = ProfilesRepository.get(this)
        if (!turnOn) {
            repo.setMode(Mode.SHARED)
            Viewer.shared.end()
        } else {
            val problem = Profiles.switchProblem(repo.profiles()) { PinRepository.guard(this, it).hasPin() }
            if (problem != null) {
                Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
                return
            }
            repo.setMode(Mode.PROFILES)
            Viewer.shared.end()
        }
        render(focusTag = TAG_MODE)
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
    private fun permissions(mode: Mode) {
        val usage = PermissionStatus.hasUsageAccess(this)
        val overlay = PermissionStatus.canDrawOverlays(this)
        if (usage && overlay) {
            val whom = if (mode == Mode.PROFILES) "Each child's limits apply to them only." else "Limits apply to everyone using this TV."
            content.addView(label("Tracking is on. $whom", sizeSp = 15f, colorId = R.color.muted).margins(this, top = 32))
            return
        }
        val card = column().apply {
            background = rounded(R.color.panel, radiusDp = 16, strokeColorId = R.color.warn)
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }
        card.addView(label("Your child's limits can't work until these are allowed", sizeSp = 20f, weight = Weight.MEDIUM, colorId = R.color.warn))
        if (!usage) {
            permissionRow(card, "Usage access", "Lets TV Limiter see which app is open.", PermissionStatus.usageAccessScreen(), PermissionStatus.usageAdb(this))
        }
        if (!overlay) {
            permissionRow(
                card,
                "Display over other apps",
                "Lets TV Limiter cover an app when its time is up.",
                PermissionStatus.overlayScreen(this),
                PermissionStatus.overlayAdb(this),
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

    /**
     * YouTube detection needs notification access. Only builds with the detection listener
     * show this. Limits work without it, so it is a separate card from the required ones.
     */
    private fun notificationAccess() {
        if (!PermissionStatus.hasNotificationListener(this)) return
        if (PermissionStatus.hasNotificationAccess(this)) {
            content.addView(label("YouTube video titles are being recorded.", sizeSp = 15f, colorId = R.color.muted).margins(this, top = 12))
            return
        }
        val card = column().apply {
            // An outline, so the panel colored button stands out inside it.
            background = rounded(R.color.bg, radiusDp = 16, strokeColorId = R.color.panel_raised)
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }
        card.addView(label("Notification access", sizeSp = 20f, weight = Weight.MEDIUM))
        card.addView(label("Lets TV Limiter see which YouTube video is playing.", sizeSp = 15f, colorId = R.color.muted))
        when (val guide = PermissionStatus.notificationAccessGuide(this)) {
            NotificationAccessGuide.OnePress -> {
                card.addView(label("Press Allow, then switch on TV Limiter.", sizeSp = 15f).margins(this, top = 12))
                card.addView(tvButton("Allow") { openSystemScreen(PermissionStatus.notificationAccessScreen()) }.margins(this, top = 12))
            }
            is NotificationAccessGuide.Steps -> {
                card.addView(label("This TV can't open that screen directly. Press Open settings, then:", sizeSp = 15f).margins(this, top = 12))
                guide.steps.forEachIndexed { i, step -> card.addView(label("${i + 1}. $step", sizeSp = 15f).margins(this, top = 4)) }
                card.addView(tvButton("Open settings") { openSystemScreen(PermissionStatus.appsScreen()) }.margins(this, top = 12))
            }
            is NotificationAccessGuide.Unsupported -> {
                card.addView(label("${guide.reason} TV Limiter can't see YouTube videos on this TV. Time limits still work.", sizeSp = 15f).margins(this, top = 12))
            }
        }
        content.addView(card.fullWidth(this, top = 24))
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

    private fun openEditor(limitId: String, profileId: String) = startActivity(ScheduleEditorActivity.intent(this, limitId, profileId))

    companion object {
        /** Set by the picker's Settings button; without it, profiles mode sends the icon to the picker. */
        const val EXTRA_FROM_PICKER = "from_picker"
        private const val REQUEST_CHANGE_PASSKEY = 42
        private const val TAG_PARENT_MODE = "parent_mode"
        private const val TAG_MODE = "mode"

        /** The child whose limits the home screen last showed, kept while the app runs. */
        private var shownProfileId: String? = null
    }
}
