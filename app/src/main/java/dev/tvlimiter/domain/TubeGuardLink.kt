package dev.tvlimiter.domain

import kotlinx.serialization.Serializable

enum class LinkStatus {
    NOT_CONNECTED,
    CONNECTED,

    /** The backend said this TV's token is no longer valid, which means a parent removed it. */
    DISCONNECTED,
}

/** A child of the family as TubeGuard names them, from the last pair or check-in reply. */
@Serializable
data class TubeGuardChild(
    val id: String,
    val name: String,
    /** True when this child's Shorts setting is Block, so the TV can block a Short before TubeGuard answers. */
    val shortsBlocked: Boolean = false,
)

/** This TV's pairing with a TubeGuard family. The token is a secret: it is never printed. */
@Serializable
data class TubeGuardLink(
    val status: LinkStatus = LinkStatus.NOT_CONNECTED,
    val deviceId: String? = null,
    val deviceToken: String? = null,
    /** The names of the children this TV is paired to, as the parent app has them. */
    val children: List<String> = emptyList(),
    /** The same children with their ids, kept current by every check-in. Empty until the first reply that lists them. */
    val childList: List<TubeGuardChild> = emptyList(),
    /** The Shorts setting that applies when no profile with a linked child is picked: Block if any of the TV's children has it. */
    val shortsBlocked: Boolean = false,
) {
    val isConnected: Boolean get() = status == LinkStatus.CONNECTED && !deviceToken.isNullOrEmpty()

    override fun toString(): String = "TubeGuardLink(status=$status, children=${children.size})"
}
