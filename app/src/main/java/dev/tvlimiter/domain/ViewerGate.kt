package dev.tvlimiter.domain

/** What the tracking service does with the app in front, on each tick. */
sealed interface Gate {
    /** Nobody picked who is watching yet: cover the app with the picker and count nothing. */
    data object ShowPicker : Gate

    /** Parent mode: nothing counts and nothing blocks. */
    data object CountNothing : Gate

    /** Count the time against this profile's limits. */
    data class CountFor(val profileId: String) : Gate
}

object ViewerGate {
    fun decide(viewer: String?, parentMode: Boolean, profiles: List<Profile>, hasPasskey: (String) -> Boolean): Gate = when {
        parentMode -> Gate.CountNothing
        // A viewer whose profile was deleted meanwhile has to pick again.
        viewer != null && profiles.any { it.id == viewer } -> Gate.CountFor(viewer)
        // One profile with no passkey to enter: the TV just uses it.
        !Profiles.needsPicker(profiles, hasPasskey) -> Gate.CountFor(profiles.single().id)
        else -> Gate.ShowPicker
    }
}
