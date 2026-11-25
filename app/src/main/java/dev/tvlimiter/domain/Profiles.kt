package dev.tvlimiter.domain

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable

/** Rough age groups. Only young children may skip the passkey. */
@Serializable
enum class AgeBand(val label: String, val passkeyOptional: Boolean) {
    YOUNG("Young (about 6)", passkeyOptional = true),
    OLDER("Older (about 10)", passkeyOptional = false),
    TEEN("Teen (about 13)", passkeyOptional = false),
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val band: AgeBand,
    /** The TubeGuard child this profile's viewing is reported for, or null for none. */
    val tubeGuardChildId: String? = null,
    /** True once a parent chose in the profile editor, so an automatic link never overrides "Not linked". */
    val linkChosen: Boolean = false,
) {
    init {
        require(Profiles.isValidId(id)) { "Bad profile id: $id" }
    }

    /** A young child without a passkey starts watching at once. Everyone else enters theirs. */
    fun startsWithoutPasskey(hasPasskey: Boolean): Boolean = band.passkeyOptional && !hasPasskey

    /** True when this profile can't be picked until a parent sets its passkey. */
    fun needsPasskey(hasPasskey: Boolean): Boolean = !band.passkeyOptional && !hasPasskey

    /** "Leo's", or "Kids'" for a name ending in s. */
    val possessive: String get() = if (name.endsWith("s", ignoreCase = true)) "$name'" else "$name's"
}

@Serializable
data class ProfilesFile(val profiles: List<Profile> = emptyList()) {
    fun find(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** Every TV has at least the default profile, created on first read from today's files. */
    fun withDefault(): ProfilesFile =
        if (profiles.any { it.id == Profiles.DEFAULT_ID }) this else copy(profiles = listOf(Profiles.defaultProfile()) + profiles)

    fun withProfile(profile: Profile): ProfilesFile = copy(
        profiles = if (profiles.any { it.id == profile.id }) profiles.map { if (it.id == profile.id) profile else it } else profiles + profile,
    )

    /** Removes a profile. The default profile holds today's files and is never removed. */
    fun without(id: String): ProfilesFile = if (id == Profiles.DEFAULT_ID) this else copy(profiles = profiles.filterNot { it.id == id })
}

object Profiles {
    /** The default profile, whose limits, usage and history are today's files. */
    const val DEFAULT_ID = "shared"

    private const val FOLDER = "profiles"
    private val ID_PATTERN = Regex("[a-z0-9]{1,32}")

    /** Ids become folder names, so only plain lowercase letters and digits are allowed. */
    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun newId(existing: Set<String>, random: () -> String = { UUID.randomUUID().toString().replace("-", "").take(12) }): String {
        while (true) {
            val id = random()
            if (isValidId(id) && id != DEFAULT_ID && id !in existing) return id
        }
    }

    /** The default profile keeps today's files where they are; each other profile's go in its own folder. */
    fun dataFile(filesDir: File, profileId: String, name: String): File =
        if (profileId == DEFAULT_ID) File(filesDir, name) else File(folder(filesDir, profileId), name)

    fun folder(filesDir: File, profileId: String): File {
        require(profileId != DEFAULT_ID && isValidId(profileId)) { "No folder for profile $profileId" }
        return File(File(filesDir, FOLDER), profileId)
    }

    /** The one profile a TV starts with. Its files are today's, where they always were. */
    fun defaultProfile() = Profile(DEFAULT_ID, "Kids", AgeBand.YOUNG)

    /**
     * Whether the TV must ask who is watching. Not while one profile is all there is and it
     * needs no passkey: then the TV works as it did before profiles.
     */
    fun needsPicker(profiles: List<Profile>, hasPasskey: (String) -> Boolean): Boolean {
        val only = profiles.singleOrNull() ?: return true
        return !only.startsWithoutPasskey(hasPasskey(only.id))
    }

    /**
     * Where a child's passkey is kept. The default profile's sits next to the parent's
     * pin.json, under another name, so the two can never be confused.
     */
    fun passkeyFile(filesDir: File, profileId: String): File =
        if (profileId == DEFAULT_ID) File(filesDir, "profile-pin.json") else File(folder(filesDir, profileId), "pin.json")
}
