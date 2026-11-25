package dev.tvlimiter.service

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.util.Base64
import dev.tvlimiter.domain.ShortArt
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Reads the still picture YouTube's media session gives a Short (about 640 by 360) and makes the small version
 * that is sent: about 320 by 180, a JPEG, as base64. The picture is read only for an untitled item that could be
 * a Short, and the last one is remembered so the same bitmap is not encoded again at every check.
 */
object ShortArtReader {
    private const val WIDTH = 320
    private const val HEIGHT = 180
    private const val QUALITY = 50

    private var lastBitmap: Bitmap? = null
    private var lastArt: ShortArt? = null

    @Synchronized
    fun read(metadata: MediaMetadata): ShortArt? {
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (bitmap === lastBitmap) return lastArt
        val scaled = if (bitmap.width == WIDTH && bitmap.height == HEIGHT) bitmap else Bitmap.createScaledBitmap(bitmap, WIDTH, HEIGHT, true)
        val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
        if (scaled !== bitmap) scaled.recycle()
        val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(32)
        return ShortArt(key, Base64.encodeToString(bytes, Base64.NO_WRAP)).also {
            lastBitmap = bitmap
            lastArt = it
        }
    }
}
