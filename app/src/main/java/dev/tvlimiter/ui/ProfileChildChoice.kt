package dev.tvlimiter.ui

import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.ProfileLinks
import dev.tvlimiter.domain.TubeGuardChild

/**
 * The profile editor's "TubeGuard child" choice: "Not linked" and each child the TV is paired to, with
 * the profile's current one marked. Only shown while the TV is connected to TubeGuard.
 */
class ProfileChildChoice(private val profile: Profile, private val children: List<TubeGuardChild>, private val connected: Boolean) {
    data class Option(val childId: String?, val label: String, val selected: Boolean)

    val visible: Boolean get() = connected

    private val current: TubeGuardChild? get() = ProfileLinks.linkedChild(profile, children)

    /** "Not linked" first, then the children in the order TubeGuard lists them. */
    val options: List<Option>
        get() = listOf(Option(null, NOT_LINKED, current == null)) + children.map { Option(it.id, it.name, it.id == current?.id) }

    val note: String
        get() = when {
            children.isEmpty() -> "TubeGuard hasn't listed this TV's children yet. They show up after the next check-in."
            current != null -> "Videos ${profile.name} watches are reported for ${current!!.name}, and ${current!!.name}'s TubeGuard settings apply."
            else -> "Videos ${profile.name} watches are checked with the strictest settings among this TV's children."
        }

    /** The profile with the parent's choice saved on it. [childId] null is "Not linked". */
    fun pick(childId: String?): Profile {
        require(childId == null || children.any { it.id == childId }) { "Unknown child" }
        return profile.copy(tubeGuardChildId = childId, linkChosen = true)
    }

    companion object {
        const val NOT_LINKED = "Not linked"
    }
}
