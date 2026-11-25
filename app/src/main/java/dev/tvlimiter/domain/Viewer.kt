package dev.tvlimiter.domain

/**
 * Who is watching, in profiles mode. Like parent mode it lives only in memory, so a restart of
 * the app, the screen going off or the screensaver clears it and the picker comes back.
 */
class Viewer {
    @Volatile var current: String? = null
        private set

    fun start(profileId: String) {
        current = profileId
    }

    fun end() {
        current = null
    }

    companion object {
        /** The one viewer shared by the picker, the block screen and the tracking service. */
        val shared = Viewer()
    }
}
