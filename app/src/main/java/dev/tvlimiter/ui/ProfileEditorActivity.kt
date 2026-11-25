package dev.tvlimiter.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvlimiter.R
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.ProfilesRepository
import dev.tvlimiter.data.TubeGuardLinkRepository
import dev.tvlimiter.service.TubeGuard
import dev.tvlimiter.domain.AgeBand
import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.Profiles

/**
 * Adds or edits one child's profile: the name, the age group, the passkey, the TubeGuard child it reports for, and deleting it.
 * A new profile starts with a copy of the shared limits.
 */
class ProfileEditorActivity : GuardedActivity() {
    private lateinit var content: LinearLayout
    private var profileId: String? = null
    private var draftName = ""
    private var draftBand = AgeBand.YOUNG
    private var confirmingDelete = false
    private var nameField: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileId = intent.getStringExtra(EXTRA_PROFILE)
        // The keyboard opens only when the parent presses OK on the name.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        content = scrollScreen()
        // The TubeGuard child choice lists the children from the last check-in, so ask for a fresh list.
        TubeGuard.checkInNow(this) {
            runOnUiThread { if (!isFinishing && !isDestroyed && SettingsUnlock.shared.isUnlocked) onUnlockedResume() }
        }
    }

    override fun onUnlockedResume() {
        val id = profileId
        if (id != null && ProfilesRepository.get(this).find(id) == null) return finish()
        build()
    }

    override fun onPause() {
        saveName()
        super.onPause()
    }

    @Deprecated("Back closes the delete question before leaving")
    override fun onBackPressed() {
        if (confirmingDelete) {
            confirmingDelete = false
            build(focusTag = TAG_DELETE)
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun profile(): Profile? = profileId?.let { ProfilesRepository.get(this).find(it) }

    private fun build(focusTag: String? = null) {
        content.removeAllViews()
        nameField = null
        val profile = profile()
        if (profile != null && confirmingDelete) return confirmDelete(profile)
        if (profile != null) {
            draftName = profile.name
            draftBand = profile.band
        }

        content.addView(label(profile?.name ?: "Add a profile", sizeSp = 34f, bold = true))

        content.addView(label("Name", sizeSp = 20f, weight = Weight.MEDIUM).margins(this, top = 28))
        val name = nameField()
        nameField = name
        content.addView(name, LinearLayout.LayoutParams(dp(480), -2).apply { topMargin = dp(10) })

        content.addView(label("Age", sizeSp = 20f, weight = Weight.MEDIUM).margins(this, top = 32))
        content.addView(label("Only young children can start watching without a passkey.", sizeSp = 15f, colorId = R.color.muted))
        val bands = row().apply { clipChildren = false }
        AgeBand.entries.forEach { band ->
            val text = if (band == draftBand) "✓ ${band.label}" else band.label
            val button = tvButton(text) { pickBand(band) }.apply { tag = band.name }
            bands.addView(button.margins(this, end = 12))
        }
        content.addView(bands.margins(this, top = 12))

        if (profile == null) {
            content.addView(tvButton("Add this profile") { add() }.margins(this, top = 36))
            (focusTag?.let { content.findViewWithTag<View>(it) } ?: name).requestFocus()
            return
        }

        passkeyRow(profile)
        childRow(profile)
        // The default profile holds the TV's original limits and history, so every TV keeps at least it.
        if (profile.id != Profiles.DEFAULT_ID) {
            content.addView(tvButton("Delete this profile") { askDelete() }.apply { tag = TAG_DELETE }.margins(this, top = 36))
        }
        (focusTag?.let { content.findViewWithTag<View>(it) } ?: bands.getChildAt(draftBand.ordinal)).requestFocus()
    }

    private fun nameField() = EditText(this).apply {
        setText(draftName)
        hint = "Type a name"
        setSingleLine()
        filters = arrayOf(InputFilter.LengthFilter(MAX_NAME))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE
        typeface = font(Weight.MEDIUM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTextColor(color(R.color.text))
        setHintTextColor(ColorStateList.valueOf(color(R.color.muted)))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.panel_raised, radiusDp = 16, strokeColorId = R.color.accent))
            addState(intArrayOf(), rounded(R.color.panel, radiusDp = 16))
        }
        setPadding(dp(24), dp(14), dp(24), dp(14))
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) saveName()
            false
        }
        setOnFocusChangeListener { _, focused -> if (!focused) saveName() }
    }

    /** Keeps the typed name; an existing profile saves it at once, unless it was left empty. */
    private fun saveName() {
        val typed = nameField?.text?.toString()?.trim() ?: return
        draftName = typed
        val profile = profile() ?: return
        if (typed.isNotEmpty() && typed != profile.name) ProfilesRepository.get(this).upsert(profile.copy(name = typed))
    }

    private fun pickBand(band: AgeBand) {
        saveName()
        draftBand = band
        val profile = profile()
        if (profile != null && profile.band != band) {
            val updated = profile.copy(band = band)
            ProfilesRepository.get(this).upsert(updated)
            // An older child can't be picked without a passkey, so ask for one right away.
            if (updated.needsPasskey(hasPasskey(updated.id))) {
                startActivity(PinSetupActivity.profileIntent(this, updated.id, updated.name))
                return
            }
        }
        build(focusTag = band.name)
    }

    private fun add() {
        saveName()
        if (draftName.isEmpty()) {
            Toast.makeText(this, "Type a name first.", Toast.LENGTH_LONG).show()
            nameField?.requestFocus()
            return
        }
        val profile = ProfilesRepository.add(this, draftName, draftBand)
        profileId = profile.id
        intent.putExtra(EXTRA_PROFILE, profile.id)
        if (profile.needsPasskey(hasPasskey(profile.id))) {
            startActivity(PinSetupActivity.profileIntent(this, profile.id, profile.name))
        } else {
            build()
        }
    }

    private fun passkeyRow(profile: Profile) {
        val has = hasPasskey(profile.id)
        val row = row().apply { clipChildren = false }
        val text = column()
        text.addView(label("Passkey", sizeSp = 20f, weight = Weight.MEDIUM))
        val note = when {
            profile.needsPasskey(has) -> "${profile.name} can't start watching until a passkey is set."
            has -> "${profile.name} enters it to start watching."
            else -> "Not needed. ${profile.name} starts watching with one press."
        }
        text.addView(label(note, sizeSp = 15f, colorId = if (profile.needsPasskey(has)) R.color.warn else R.color.muted))
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(24) })
        row.addView(
            tvButton(if (has) "Change passkey" else "Set a passkey") {
                startActivity(PinSetupActivity.profileIntent(this, profile.id, profile.name))
            }.margins(this, end = if (has && profile.band.passkeyOptional) 12 else 0),
        )
        if (has && profile.band.passkeyOptional) {
            row.addView(
                tvButton("Remove passkey") {
                    PinRepository.guard(this, profile.id).clearPin()
                    build(focusTag = TAG_DELETE)
                },
            )
        }
        content.addView(row.fullWidth(this, top = 36))
    }

    /** Which TubeGuard child this profile reports for. Only while the TV is connected to TubeGuard. */
    private fun childRow(profile: Profile) {
        val choice = ProfileChildChoice(
            profile,
            TubeGuardLinkRepository.get(this).current().childList,
            connected = TubeGuard.isConnected(this),
        )
        if (!choice.visible) return
        content.addView(label("TubeGuard child", sizeSp = 20f, weight = Weight.MEDIUM).margins(this, top = 36))
        content.addView(label(choice.note, sizeSp = 15f, colorId = R.color.muted))
        val options = row().apply { clipChildren = false }
        choice.options.forEach { option ->
            val text = if (option.selected) "✓ ${option.label}" else option.label
            val button = tvButton(text) {
                ProfilesRepository.get(this).upsert(choice.pick(option.childId))
                build(focusTag = TAG_CHILD + option.childId)
            }.apply { tag = TAG_CHILD + option.childId }
            options.addView(button.margins(this, end = 12))
        }
        content.addView(options.margins(this, top = 12))
    }

    private fun askDelete() {
        confirmingDelete = true
        build()
    }

    private fun confirmDelete(profile: Profile) {
        content.addView(label("Delete ${profile.name}?", sizeSp = 34f, bold = true))
        content.addView(
            label("${profile.possessive} limits, screen time history and passkey will be deleted too.", sizeSp = 18f, colorId = R.color.muted)
                .margins(this, top = 12),
        )
        val buttons = row().apply { clipChildren = false }
        val keep = tvButton("Keep ${profile.name}") {
            confirmingDelete = false
            build(focusTag = TAG_DELETE)
        }
        buttons.addView(keep.margins(this, end = 12))
        buttons.addView(
            tvButton("Delete") {
                ProfilesRepository.delete(this, profile.id)
                finish()
            },
        )
        content.addView(buttons.margins(this, top = 32))
        keep.requestFocus()
    }

    private fun hasPasskey(id: String) = PinRepository.guard(this, id).hasPin()

    companion object {
        private const val EXTRA_PROFILE = "profile"
        private const val TAG_DELETE = "delete"
        private const val TAG_CHILD = "child:"
        private const val MAX_NAME = 20

        /** Edits the profile [profileId], or adds a new one when it is null. */
        fun intent(context: Context, profileId: String?): Intent =
            Intent(context, ProfileEditorActivity::class.java).apply { profileId?.let { putExtra(EXTRA_PROFILE, it) } }
    }
}
