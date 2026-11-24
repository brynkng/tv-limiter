package dev.tvlimiter.domain

import dev.tvlimiter.domain.NotificationAccessGuide.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessGuideTest {
    private fun device(
        sdkInt: Int = 34,
        isFireTv: Boolean = false,
        isLowRam: Boolean = false,
        accessScreenOpens: Boolean = false,
        appsScreenOpens: Boolean = true,
    ) = NotificationAccessGuide.forDevice(Device(sdkInt, isFireTv, isLowRam, accessScreenOpens, appsScreenOpens))

    @Test
    fun opensTheScreenWithOnePressWhenItExists() {
        assertEquals(NotificationAccessGuide.OnePress, device(sdkInt = 31, accessScreenOpens = true))
    }

    @Test
    fun prefersTheScreenEvenOnFireTvOrAnOldTvThatHasIt() {
        assertEquals(NotificationAccessGuide.OnePress, device(isFireTv = true, accessScreenOpens = true))
        assertEquals(NotificationAccessGuide.OnePress, device(sdkInt = 26, accessScreenOpens = true))
    }

    @Test
    fun givesStepsOnAndroid9To11() {
        for (sdk in 28..30) {
            assertEquals(NotificationAccessGuide.Steps(NotificationAccessGuide.STEPS), device(sdkInt = sdk))
        }
    }

    @Test
    fun cannotSupportFireTv() {
        assertTrue(device(sdkInt = 28, isFireTv = true) is NotificationAccessGuide.Unsupported)
    }

    @Test
    fun cannotSupportAndroid8() {
        assertTrue(device(sdkInt = 27) is NotificationAccessGuide.Unsupported)
    }

    @Test
    fun cannotSupportLowMemoryTvsOn11OrOlder() {
        assertTrue(device(sdkInt = 30, isLowRam = true) is NotificationAccessGuide.Unsupported)
        assertEquals(NotificationAccessGuide.Steps(NotificationAccessGuide.STEPS), device(sdkInt = 30, isLowRam = false))
    }

    @Test
    fun cannotSupportATvWithNoAppsScreen() {
        assertTrue(device(sdkInt = 30, appsScreenOpens = false) is NotificationAccessGuide.Unsupported)
    }
}
