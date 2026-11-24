package dev.tvlimiter.detection

object Targets {
    private val PREFIXES = listOf(
        "com.google.android.youtube", // phone and Google TV (includes .tv)
        "com.amazon.firetv.youtube", // Fire TV
        "com.android.chrome",
        "com.chrome.", // beta, dev, canary
    )

    fun isTarget(packageName: String): Boolean = PREFIXES.any { packageName.startsWith(it) }
}
