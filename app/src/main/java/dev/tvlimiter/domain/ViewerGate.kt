package dev.tvlimiter.domain

/** What the tracking service does with the app in front, on each tick. */
sealed interface Gate {
    /** Profiles mode and nobody picked yet: cover the app with the picker and count nothing. */
    data object ShowPicker : Gate

    /** Parent mode: nothing counts and nothing blocks. */
    data object CountNothing : Gate

    /** Count the time against this profile's limits. */
    data class CountFor(val profileId: String) : Gate
}

object ViewerGate {
    fun decide(mode: Mode, viewer: String?, parentMode: Boolean, profileIds: Collection<String>): Gate = when {
        parentMode -> Gate.CountNothing
        mode == Mode.SHARED -> Gate.CountFor(Profiles.SHARED_ID)
        // A viewer whose profile was deleted meanwhile has to pick again.
        viewer != null && viewer in profileIds -> Gate.CountFor(viewer)
        else -> Gate.ShowPicker
    }
}
