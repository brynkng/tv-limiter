package dev.tvlimiter.domain

/** A Short's still picture, scaled down and encoded as a JPEG in base64, with a hash that tells one Short from the next. */
data class ShortArt(val key: String, val jpegBase64: String)

/**
 * YouTube Shorts report no title or channel through the media session, only a length and a still picture. A regular
 * video is also untitled for a moment before its title loads, so "untitled" only counts as a Short once it has
 * played for [WatchTracker.Rules.minPlayMs] and is still untitled.
 */
object Shorts {
    /** The longest a Short can be. Anything longer that has no title is not guessed at. */
    const val MAX_LENGTH_MS = 240_000L
    private const val MIN_LENGTH_MS = 1_000L

    fun couldBeShort(lengthMs: Long): Boolean = lengthMs in MIN_LENGTH_MS..MAX_LENGTH_MS

    /**
     * Whether Shorts are blocked for the viewer now: the setting of the child linked to the picked profile, else
     * the setting for the whole TV. It is the one TubeGuard last told this TV.
     */
    fun blocked(profileId: String?, profiles: List<Profile>, link: TubeGuardLink): Boolean {
        val childId = ProfileLinks.childIdFor(profileId, profiles, link.childList)
        val child = childId?.let { id -> link.childList.firstOrNull { it.id == id } }
        return child?.shortsBlocked ?: link.shortsBlocked
    }
}
