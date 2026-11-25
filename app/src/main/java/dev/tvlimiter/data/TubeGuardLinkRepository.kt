package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.LinkStatus
import dev.tvlimiter.domain.TubeGuardChild
import dev.tvlimiter.domain.TubeGuardLink
import java.io.File

/**
 * This TV's pairing with TubeGuard, in tubeguard.json in the app's private storage. It holds the
 * device token, so nothing reads it out but the code that sends requests.
 */
class TubeGuardLinkRepository(private val store: JsonFileStore<TubeGuardLink>) {
    private val lock = Any()
    private var cached: TubeGuardLink? = null

    fun current(): TubeGuardLink = synchronized(lock) { cached ?: store.read().also { cached = it } }

    fun connect(deviceId: String, token: String, children: List<TubeGuardChild>) =
        save(TubeGuardLink(LinkStatus.CONNECTED, deviceId, token, children.map { it.name }, children))

    /** The children the backend listed in its latest reply. Ignored once this TV is no longer connected. */
    fun updateChildren(children: List<TubeGuardChild>) = synchronized(lock) {
        val now = current()
        if (now.status == LinkStatus.CONNECTED && (now.childList != children || now.children != children.map { it.name })) {
            save(now.copy(children = children.map { it.name }, childList = children))
        }
    }

    /** The backend refused the token. The token is dropped; the children's names stay for the message. */
    fun markDisconnected() = synchronized(lock) {
        val now = current()
        if (now.status == LinkStatus.CONNECTED) save(TubeGuardLink(LinkStatus.DISCONNECTED, children = now.children, childList = now.childList))
    }

    /** A parent disconnects this TV on purpose. */
    fun forget() = save(TubeGuardLink())

    private fun save(link: TubeGuardLink) = synchronized(lock) {
        store.write(link)
        cached = link
    }

    companion object {
        @Volatile private var instance: TubeGuardLinkRepository? = null

        fun get(context: Context): TubeGuardLinkRepository = instance ?: synchronized(this) {
            instance ?: TubeGuardLinkRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "tubeguard.json"), TubeGuardLink.serializer()) {
                    TubeGuardLink()
                },
            ).also { instance = it }
        }
    }
}
