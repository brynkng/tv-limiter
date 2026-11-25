package dev.tvlimiter.detection

import java.security.MessageDigest

/** One stable file name per app, title and channel, so a rewatch reuses the saved image. */
object ThumbnailNames {
    fun fileName(pkg: String, title: String, artist: String?): String {
        val key = listOf(pkg, title.trim().lowercase(), artist.orEmpty().trim().lowercase()).joinToString("\n")
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return "${hash.take(16)}.jpg"
    }
}
