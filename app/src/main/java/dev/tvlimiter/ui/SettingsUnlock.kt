package dev.tvlimiter.ui

/**
 * Remembers that the owner entered the PIN, only while the settings screens stay on screen.
 * Every settings screen reports start and stop. When none is started any more (the owner pressed
 * Home or the task went to the background), the unlock is dropped, so coming back from recents or
 * from a launcher asks for the PIN again.
 */
class SettingsUnlock {
    private var started = 0

    var isUnlocked: Boolean = false
        private set

    fun unlock() {
        isUnlocked = true
    }

    fun onStarted() {
        started++
    }

    fun onStopped() {
        started = maxOf(0, started - 1)
        if (started == 0) isUnlocked = false
    }

    companion object {
        val shared = SettingsUnlock()
    }
}
