package dev.tvlimiter.domain

/**
 * Which TubeGuard child each TV Limiter profile reports for. A profile keeps the id of the child a
 * parent chose. If TubeGuard no longer lists that child (a parent removed it from this TV in the parent
 * app), the profile counts as not linked.
 */
object ProfileLinks {
    /** The child [profile] is linked to, or null when it has no link or its child is no longer listed. */
    fun linkedChild(profile: Profile, children: List<TubeGuardChild>): TubeGuardChild? =
        profile.tubeGuardChildId?.let { id -> children.firstOrNull { it.id == id } }

    /** The id a report for [profileId] carries, or null for no profile or no link. */
    fun childIdFor(profileId: String?, profiles: List<Profile>, children: List<TubeGuardChild>): String? {
        val profile = profiles.firstOrNull { it.id == profileId } ?: return null
        return linkedChild(profile, children)?.id
    }

    /**
     * The default profile linked to the TV's only child, when the TV has just that profile, TubeGuard lists
     * exactly one child, and no parent has chosen otherwise. Null when nothing should change.
     */
    fun autoLinked(profiles: List<Profile>, children: List<TubeGuardChild>): Profile? {
        val only = profiles.singleOrNull() ?: return null
        val child = children.singleOrNull() ?: return null
        if (only.id != Profiles.DEFAULT_ID || only.linkChosen || linkedChild(only, children) != null) return null
        return only.copy(tubeGuardChildId = child.id)
    }
}
