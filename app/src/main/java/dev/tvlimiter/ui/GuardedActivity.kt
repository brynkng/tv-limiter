package dev.tvlimiter.ui

import android.app.Activity
import android.content.Intent

/** Base for the settings screens. Each one counts toward the unlock and asks for the PIN when locked. */
abstract class GuardedActivity : Activity() {
    private var awaitingUnlock = false

    override fun onStart() {
        super.onStart()
        SettingsUnlock.shared.onStarted()
        // A result from the PIN screen arrives after onStart, so don't open a second one meanwhile.
        if (!awaitingUnlock && !SettingsUnlock.shared.isUnlocked) {
            awaitingUnlock = true
            @Suppress("DEPRECATION")
            startActivityForResult(unlockIntent(), REQUEST_UNLOCK)
        }
    }

    override fun onResume() {
        super.onResume()
        if (SettingsUnlock.shared.isUnlocked) onUnlockedResume()
    }

    override fun onStop() {
        super.onStop()
        SettingsUnlock.shared.onStopped()
    }

    @Deprecated("Framework activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_UNLOCK) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        awaitingUnlock = false
        if (resultCode != RESULT_OK) finish()
    }

    /** The screen that unlocks settings. The main screen swaps in passkey setup when none exists yet. */
    protected open fun unlockIntent(): Intent = skeleton.into(Intent(this, PinGateActivity::class.java))

    /** The placeholder the passkey screen shows while this screen loads after a right passkey. */
    protected open val skeleton: Skeleton = Skeleton.LIST

    /** Called on every resume once the owner has unlocked settings. */
    protected abstract fun onUnlockedResume()

    private companion object {
        const val REQUEST_UNLOCK = 41
    }
}

/** Base for the PIN screens themselves: they count toward the unlock but never ask for it. */
abstract class TrackedActivity : Activity() {
    override fun onStart() {
        super.onStart()
        SettingsUnlock.shared.onStarted()
    }

    override fun onStop() {
        super.onStop()
        SettingsUnlock.shared.onStopped()
    }
}
