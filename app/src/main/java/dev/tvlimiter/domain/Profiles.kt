package dev.tvlimiter.domain

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable

/** Shared: one set of limits for everyone. Profiles: each child picks who is watching. */
@Serializable
enum class Mode { SHARED, PROFILES }

/** Rough age groups. Only young children may skip the passkey. */
@Serializable
enum class AgeBand(val label: String, val passkeyOptional: Boolean) {
    YOUNG("Young (about 6)", passkeyOptional = true),
    OLDER("Older (about 10)", passkeyOptional = false),
    TEEN("Teen (about 13)", passkeyOptional = false),
}

@Serializable
data class Profile(val id: String, val name: String, val band: AgeBand) {
    init {
        require(Profiles.isValidId(id)) { "Bad profile id: $id" }
    }

    /** A young child without a passkey starts watching at once. Everyone else enters theirs. */
    fun startsWithoutPasskey(hasPasskey: Boolean): Boolean = band.passkeyOptional && !hasPasskey

    /** True when this profile can't be picked until a parent sets its passkey. */
    fun needsPasskey(hasPasskey: Boolean): Boolean = !band.passkeyOptional && !hasPasskey
}

@Serializable
data class ProfilesFile(val mode: Mode = Mode.SHARED, val profiles: List<Profile> = emptyList()) {
    fun find(id: String): Profile? = profiles.firstOrNull { it.id == id }

    fun withProfile(profile: Profile): ProfilesFile = copy(
        profiles = if (profiles.any { it.id == profile.id }) profiles.map { if (it.id == profile.id) profile else it } else profiles + profile,
    )

    /** Removes a profile. Profiles mode with nobody left falls back to shared mode. */
    fun without(id: String): ProfilesFile {
        val rest = profiles.filterNot { it.id == id }
        return copy(profiles = rest, mode = if (rest.isEmpty()) Mode.SHARED else mode)
    }
}

object Profiles {
    /** The profile behind today's files: the shared limits, and the parent passkey. */
    const val SHARED_ID = "shared"

    private const val FOLDER = "profiles"
    private val ID_PATTERN = Regex("[a-z0-9]{1,32}")

    /** Ids become folder names, so only plain lowercase letters and digits are allowed. */
    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun newId(existing: Set<String>, random: () -> String = { UUID.randomUUID().toString().replace("-", "").take(12) }): String {
        while (true) {
            val id = random()
            if (isValidId(id) && id != SHARED_ID && id !in existing) return id
        }
    }

    /** The shared profile keeps today's files where they are; each child's go in its own folder. */
    fun dataFile(filesDir: File, profileId: String, name: String): File =
        if (profileId == SHARED_ID) File(filesDir, name) else File(folder(filesDir, profileId), name)

    fun folder(filesDir: File, profileId: String): File {
        require(profileId != SHARED_ID && isValidId(profileId)) { "No folder for profile $profileId" }
        return File(File(filesDir, FOLDER), profileId)
    }

    /**
     * Why profiles mode can't be turned on yet, in words for the parent, or null when it can.
     * It needs at least one child, and every child who must use a passkey needs one set.
     */
    fun switchProblem(profiles: List<Profile>, hasPasskey: (String) -> Boolean): String? {
        if (profiles.isEmpty()) return "Add a profile first."
        val missing = profiles.firstOrNull { it.needsPasskey(hasPasskey(it.id)) } ?: return null
        return "Set a passkey for ${missing.name} first."
    }
}
