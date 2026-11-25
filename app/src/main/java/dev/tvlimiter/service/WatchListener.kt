package dev.tvlimiter.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.domain.Playing
import dev.tvlimiter.domain.WatchTracker
import dev.tvlimiter.domain.YouTubePackages

/**
 * Follows YouTube's media session and reports what the child watches to TubeGuard. It is a thin
 * adapter: it reads the session's title, channel, length and play state, and [WatchTracker] decides
 * what counts as a watch.
 *
 * It does nothing until the TV is paired, and nothing while parent mode is on. Debug builds extend this
 * class (the YouTube detection recorder), so the notification access already granted to the recorder keeps working.
 */
open class WatchListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private val tracker = WatchTracker()
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private var connected = false

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        follow(controllers ?: emptyList())
    }

    private val tick = object : Runnable {
        override fun run() {
            sample()
            if (connected) main.postDelayed(this, SAMPLE_EVERY_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        running = this
        TrackingService.refreshNotice(this)
        if (!TubeGuard.enabled) return
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            val me = ComponentName(this, javaClass)
            manager.addOnActiveSessionsChangedListener(sessionsChanged, me)
            follow(manager.getActiveSessions(me))
        } catch (e: SecurityException) {
            // Access was taken away between the connection and this call. onListenerDisconnected follows.
        }
        main.post(tick)
    }

    override fun onListenerDisconnected() {
        connected = false
        if (running === this) running = null
        main.removeCallbacks(tick)
        try {
            getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (_: Exception) {
        }
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        // Whatever has played enough is reported now, rather than lost with the listener.
        if (TubeGuard.isConnected(this) && !ParentMode.shared.active) tracker.finish(TubeGuard.now()).forEach { TubeGuard.submit(this, it) }
        TrackingService.refreshNotice(this)
        super.onListenerDisconnected()
    }

    private fun follow(controllers: List<MediaController>) {
        val youtube = controllers.filter { YouTubePackages.isYouTube(it.packageName) }
        callbacks.keys.filter { it !in youtube }.forEach { callbacks.remove(it)?.let { cb -> it.unregisterCallback(cb) } }
        for (controller in youtube) {
            if (controller in callbacks) continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = sample()
                override fun onPlaybackStateChanged(state: PlaybackState?) = sample()
                override fun onSessionDestroyed() = sample()
            }
            controller.registerCallback(callback, main)
            callbacks[controller] = callback
        }
        sample()
    }

    /** Reads the session now and hands it to the tracker. Runs on the main thread. */
    private fun sample() {
        if (!connected || !TubeGuard.enabled) return
        val suppressed = ParentMode.shared.active || !TubeGuard.isConnected(this)
        val playing = callbacks.keys.firstNotNullOfOrNull { playing(it) }
        tracker.update(TubeGuard.now(), playing, suppressed).forEach { TubeGuard.submit(this, it) }
        // A video already found over a limit is blocked as soon as it plays. Parent mode and an unpaired TV block nothing.
        if (!suppressed) TubeGuard.blocker(this).onPlaying(playing)
        TubeGuard.pollBlocks(this)
    }

    /** Pauses YouTube. Its TV app takes pause and play from other apps but not stop, and "next" would start an unchecked video. */
    fun pauseYouTube() {
        main.post { callbacks.keys.forEach { runCatching { it.transportControls.pause() } } }
    }

    /** Lets a video a parent allowed play again. */
    fun playYouTube() {
        main.post { callbacks.keys.forEach { runCatching { it.transportControls.play() } } }
    }

    private fun playing(controller: MediaController): Playing? {
        if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return null
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val channel = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        return Playing(title, channel, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
    }

    companion object {
        private const val SAMPLE_EVERY_MS = 3_000L

        /** The connected listener, for the block screen to pause and resume YouTube through. */
        @Volatile var running: WatchListener? = null
            private set
    }
}
