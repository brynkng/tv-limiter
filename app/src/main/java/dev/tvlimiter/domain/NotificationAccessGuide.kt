package dev.tvlimiter.domain

/**
 * How a parent can switch on notification access on this TV, which lets TV Limiter read
 * what YouTube is playing.
 *
 * Android TV 12 and newer has a screen any app can open. Android TV 9 to 11 has the same
 * screen, but only by walking through Settings. Android TV 8, low-memory boxes on 11 or
 * older, and Fire TV have no screen at all, so only a computer with adb can switch it on.
 */
sealed interface NotificationAccessGuide {
    /** The notification access list opens with one press. */
    data object OnePress : NotificationAccessGuide

    /** Open the Apps settings and follow these steps. */
    data class Steps(val steps: List<String>) : NotificationAccessGuide

    /** This TV has no way for a parent to switch it on. */
    data class Unsupported(val reason: String) : NotificationAccessGuide

    data class Device(
        val sdkInt: Int,
        val isFireTv: Boolean,
        val isLowRam: Boolean,
        /** Whether the notification access screen opens directly on this TV. */
        val accessScreenOpens: Boolean,
        /** Whether the Apps settings screen opens on this TV. */
        val appsScreenOpens: Boolean,
    )

    companion object {
        const val ANDROID_9 = 28
        const val ANDROID_11 = 30

        val STEPS = listOf(
            "Open Special app access",
            "Open Notification access",
            "Switch on TV Limiter",
        )

        fun forDevice(d: Device): NotificationAccessGuide = when {
            // Checked first, so a TV that has the screen gets it whatever its version or maker.
            d.accessScreenOpens -> OnePress
            d.isFireTv -> Unsupported("Fire TV doesn't let apps turn on notification access.")
            d.sdkInt < ANDROID_9 -> Unsupported("This TV's Android version is too old to turn on notification access.")
            d.isLowRam && d.sdkInt <= ANDROID_11 -> Unsupported("This TV doesn't have enough memory for notification access.")
            d.appsScreenOpens -> Steps(STEPS)
            else -> Unsupported("This TV doesn't have a notification access screen.")
        }
    }
}
