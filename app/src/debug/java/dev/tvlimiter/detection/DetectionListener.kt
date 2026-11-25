package dev.tvlimiter.detection

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DetectionListener : NotificationListenerService() {
    private val callbacks = HashMap<String, Pair<MediaController, MediaController.Callback>>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attach(controllers ?: emptyList())
    }

    override fun onListenerConnected() {
        EventLog.write(
            this, "listener_connected",
            listOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "sdk_int" to Build.VERSION.SDK_INT,
                "release" to Build.VERSION.RELEASE,
                "fingerprint" to Build.FINGERPRINT,
            ),
        )
        val manager = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, DetectionListener::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, me)
            attach(manager.getActiveSessions(me))
        } catch (e: Exception) {
            EventLog.write(this, "error", listOf("where" to "media_session_setup", "message" to e.toString()))
        }
    }

    override fun onListenerDisconnected() {
        EventLog.write(this, "listener_disconnected", emptyList())
        try {
            getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (_: Exception) {
        }
        callbacks.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
    }

    private fun attach(controllers: List<MediaController>) {
        // Every package is listed so a missing or unexpected package name shows up in the log.
        EventLog.write(this, "sessions", listOf("packages" to controllers.map { it.packageName }))
        val live = controllers.map { it.packageName }.toSet()
        callbacks.keys.filter { it !in live }.forEach { pkg ->
            callbacks.remove(pkg)?.let { (c, cb) -> c.unregisterCallback(cb) }
        }
        for (controller in controllers) {
            val pkg = controller.packageName
            // Every app's session is followed, so we learn what each streaming app reports.
            if (callbacks.containsKey(pkg)) continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = logMetadata(controller, metadata)
                override fun onPlaybackStateChanged(state: PlaybackState?) = logPlayback(controller, state)
                override fun onSessionDestroyed() {
                    EventLog.write(this@DetectionListener, "session_destroyed", listOf("package" to pkg))
                    callbacks.remove(pkg)
                }
                override fun onExtrasChanged(extras: Bundle?) =
                    EventLog.write(this@DetectionListener, "session_extras", listOf("package" to pkg, "extras" to dump(extras)))
            }
            controller.registerCallback(callback)
            callbacks[pkg] = controller to callback
            logSession(controller)
            logMetadata(controller, controller.metadata)
            logPlayback(controller, controller.playbackState)
        }
    }

    private fun logSession(c: MediaController) {
        val fields = mutableListOf<Pair<String, Any?>>(
            "package" to c.packageName,
            "session_activity_creator" to c.sessionActivity?.creatorPackage,
            "session_extras" to dump(c.extras),
            "queue_title" to c.queueTitle?.toString(),
            "queue" to c.queue?.take(10)?.map {
                mapOf(
                    "id" to it.queueId,
                    "title" to it.description.title?.toString(),
                    "subtitle" to it.description.subtitle?.toString(),
                    "media_id" to it.description.mediaId,
                    "media_uri" to it.description.mediaUri?.toString(),
                    "extras" to dump(it.description.extras),
                )
            },
            "playback_info_type" to c.playbackInfo?.playbackType,
        )
        if (Build.VERSION.SDK_INT >= 30) {
            fields += "tag" to c.tag
            fields += "session_info" to dump(c.sessionInfo)
        }
        EventLog.write(this, "session", fields)
    }

    private fun logMetadata(c: MediaController, m: MediaMetadata?) {
        val values = LinkedHashMap<String, Any?>()
        m?.keySet()?.sorted()?.forEach { key -> values[key] = metadataValue(m, key) }
        val description = m?.description
        val thumbnail = m?.let { saveThumbnail(c.packageName, it) }
        EventLog.write(
            this, "metadata",
            listOf(
                "package" to c.packageName,
                "values" to values,
                "thumbnail_file" to thumbnail,
                "description" to mapOf(
                    "media_id" to description?.mediaId,
                    "media_uri" to description?.mediaUri?.toString(),
                    "title" to description?.title?.toString(),
                    "subtitle" to description?.subtitle?.toString(),
                    "extras" to dump(description?.extras),
                ),
            ),
        )
    }

    private fun logPlayback(c: MediaController, s: PlaybackState?) {
        EventLog.write(
            this, "playback",
            listOf(
                "package" to c.packageName,
                "state" to s?.state,
                "state_name" to s?.state?.let(::stateName),
                "position_ms" to s?.position,
                "buffered_ms" to s?.bufferedPosition,
                "speed" to s?.playbackSpeed,
                "actions" to s?.actions,
                "active_queue_item_id" to s?.activeQueueItemId,
                "error" to s?.errorMessage?.toString(),
                "extras" to dump(s?.extras),
            ),
        )
    }

    /** Saves the session's image once per title, so it can be looked at next to the log. */
    private fun saveThumbnail(pkg: String, m: MediaMetadata): String? {
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) return null
        val art = IMAGE_KEYS.firstNotNullOfOrNull { key -> runCatching { m.getBitmap(key) }.getOrNull() } ?: return null
        val name = ThumbnailNames.fileName(pkg, title, m.getString(MediaMetadata.METADATA_KEY_ARTIST))
        return try {
            val file = java.io.File(EventLog.file(this).parentFile, "thumbs/$name")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.outputStream().use { art.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
            "thumbs/$name"
        } catch (e: Exception) {
            EventLog.write(this, "error", listOf("where" to "save_thumbnail", "message" to e.toString()))
            null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = logNotification("notification_posted", sbn)
    override fun onNotificationRemoved(sbn: StatusBarNotification) = logNotification("notification_removed", sbn)

    private fun logNotification(event: String, sbn: StatusBarNotification) {
        // Media notifications from any app, plus everything from YouTube and Chrome.
        if (!Targets.isTarget(sbn.packageName) && sbn.notification.extras?.get("android.mediaSession") == null) return
        val n = sbn.notification
        EventLog.write(
            this, event,
            listOf(
                "package" to sbn.packageName,
                "id" to sbn.id,
                "tag" to sbn.tag,
                "key" to sbn.key,
                "category" to n.category,
                "channel_id" to n.channelId,
                "ongoing" to sbn.isOngoing,
                "content_intent_creator" to n.contentIntent?.creatorPackage,
                // Title, text and sub text often carry the video title, channel and, for Chrome, the page origin.
                "extras" to dump(n.extras),
                "media_session_token_present" to (n.extras?.get("android.mediaSession") != null),
            ),
        )
    }

    private fun metadataValue(m: MediaMetadata, key: String): Any? = when (key) {
        in LONG_KEYS -> m.getLong(key)
        in BITMAP_KEYS -> m.getBitmap(key)?.let { "bitmap ${it.width}x${it.height}" }
        in RATING_KEYS -> m.getRating(key)?.toString()
        else -> m.getText(key)?.toString() ?: m.getLong(key).takeIf { it != 0L }
    }

    private fun stateName(state: Int) = when (state) {
        PlaybackState.STATE_NONE -> "none"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_FAST_FORWARDING -> "fast_forwarding"
        PlaybackState.STATE_REWINDING -> "rewinding"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_ERROR -> "error"
        PlaybackState.STATE_CONNECTING -> "connecting"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "skipping_to_previous"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "skipping_to_next"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "skipping_to_queue_item"
        else -> "unknown"
    }

    @Suppress("DEPRECATION")
    private fun dump(b: Bundle?, depth: Int = 0): Map<String, Any?>? {
        if (b == null) return null
        val out = LinkedHashMap<String, Any?>()
        try {
            for (key in b.keySet().sorted()) {
                val v = b.get(key)
                out[key] = when {
                    v is Bundle && depth < 3 -> dump(v, depth + 1)
                    v is Array<*> -> v.map { it.toString() }
                    v == null -> null
                    v is CharSequence -> v.toString()
                    v is Number || v is Boolean -> v
                    else -> "${v.javaClass.simpleName}: $v"
                }
            }
        } catch (e: Exception) {
            out["_dump_error"] = e.toString()
        }
        return out
    }

    companion object {
        private val LONG_KEYS = setOf(
            MediaMetadata.METADATA_KEY_DURATION, MediaMetadata.METADATA_KEY_YEAR,
            MediaMetadata.METADATA_KEY_TRACK_NUMBER, MediaMetadata.METADATA_KEY_NUM_TRACKS,
            MediaMetadata.METADATA_KEY_DISC_NUMBER, MediaMetadata.METADATA_KEY_BT_FOLDER_TYPE,
            "android.media.metadata.ADVERTISEMENT", "android.media.metadata.DOWNLOAD_STATUS",
        )
        private val BITMAP_KEYS = setOf(
            MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )
        private val IMAGE_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )
        private val RATING_KEYS = setOf(MediaMetadata.METADATA_KEY_USER_RATING, MediaMetadata.METADATA_KEY_RATING)
    }
}
