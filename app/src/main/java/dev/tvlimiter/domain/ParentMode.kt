package dev.tvlimiter.domain

/**
 * Parent mode: nothing is counted or blocked until the screensaver starts or the TV goes to sleep. It lives only in
 * memory, so a restart of the app always ends it rather than leaving the TV unlimited.
 */
class ParentMode {
    @Volatile var active = false
        private set

    fun start() {
        active = true
    }

    fun end() {
        active = false
    }

    companion object {
        /** The one switch shared by the block screen, the settings and the tracking service. */
        val shared = ParentMode()
    }
}
