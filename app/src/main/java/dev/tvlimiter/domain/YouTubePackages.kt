package dev.tvlimiter.domain

/** The YouTube apps whose media sessions TubeGuard follows. */
object YouTubePackages {
    /** YouTube on phones and Google TV (including `.tv`), and on Fire TV. */
    fun isYouTube(packageName: String): Boolean =
        packageName.startsWith("com.google.android.youtube") || packageName.startsWith("com.amazon.firetv.youtube")
}
