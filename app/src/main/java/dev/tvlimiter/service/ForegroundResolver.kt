package dev.tvlimiter.service

/** One activity lifecycle event, adapted from UsageEvents so the resolver stays plain Kotlin. */
data class AppEvent(val packageName: String, val type: Type, val timeMillis: Long) {
    enum class Type { RESUMED, PAUSED }
}

/**
 * Works out which app is in front. A resumed event makes that package current, and a pause
 * of the current package with nothing resumed after it means nothing is in front.
 */
object ForegroundResolver {
    fun resolve(previous: String?, events: List<AppEvent>): String? {
        var current = previous
        for (event in events.sortedBy { it.timeMillis }) {
            when (event.type) {
                AppEvent.Type.RESUMED -> current = event.packageName
                AppEvent.Type.PAUSED -> if (current == event.packageName) current = null
            }
        }
        return current
    }
}
