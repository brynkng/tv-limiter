package dev.tvlimiter.domain

import kotlinx.serialization.Serializable

enum class LinkStatus {
    NOT_CONNECTED,
    CONNECTED,

    /** The backend said this TV's token is no longer valid, which means a parent removed it. */
    DISCONNECTED,
}

/** This TV's pairing with a TubeGuard family. The token is a secret: it is never printed. */
@Serializable
data class TubeGuardLink(
    val status: LinkStatus = LinkStatus.NOT_CONNECTED,
    val deviceId: String? = null,
    val deviceToken: String? = null,
    /** The names of the children this TV is paired to, as the parent app has them. */
    val children: List<String> = emptyList(),
) {
    val isConnected: Boolean get() = status == LinkStatus.CONNECTED && !deviceToken.isNullOrEmpty()

    override fun toString(): String = "TubeGuardLink(status=$status, children=${children.size})"
}
