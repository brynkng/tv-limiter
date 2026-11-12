package dev.tvlimiter.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickAccountantTest {
    private val accountant = TickAccountant(intervalMillis = 2_000)

    @Test
    fun `the first reading charges nothing and a 2 s tick charges 2 s`() {
        assertEquals(0L, accountant.secondsToCharge(10_000, true))
        assertEquals(2L, accountant.secondsToCharge(12_000, true))
    }

    @Test
    fun `a long stall charges at most two intervals`() {
        accountant.secondsToCharge(0, true)
        assertEquals(4L, accountant.secondsToCharge(600_000, true))
    }

    @Test
    fun `a clock that went backwards charges nothing`() {
        accountant.secondsToCharge(50_000, true)
        assertEquals(0L, accountant.secondsToCharge(1_000, true))
    }

    @Test
    fun `leftover milliseconds carry into the next tick`() {
        accountant.secondsToCharge(0, true)
        val total = (1..10).sumOf { accountant.secondsToCharge(it * 1_500L, true) }
        assertEquals(15L, total)
    }

    @Test
    fun `time in an exempt app is never charged`() {
        accountant.secondsToCharge(0, true)
        assertEquals(0L, accountant.secondsToCharge(2_000, false))
        val exempt = ExemptPackages("dev.tvlimiter", setOf("com.google.android.tvlauncher"))
        assertTrue(exempt.isExempt("dev.tvlimiter"))
        assertTrue(exempt.isExempt("com.google.android.tvlauncher"))
        assertFalse(exempt.isExempt("com.netflix.ninja"))
    }

    @Test
    fun `the settings fallback home screen is not treated as a launcher`() {
        val resolvers = listOf("com.google.android.tvlauncher" to 0, "com.android.tv.settings" to -1000)
        assertEquals(setOf("com.google.android.tvlauncher"), ExemptPackages.launchers(resolvers))
    }

    @Test
    fun `only the default launcher is exempt, not every app with a home screen`() {
        val resolvers = { listOf("com.google.android.tvlauncher" to 0, "com.example.kioskplayer" to 0) }
        assertEquals(setOf("com.google.android.tvlauncher"), ExemptPackages.homes("com.google.android.tvlauncher", resolvers))
    }

    @Test
    fun `with no default launcher chosen every real launcher stays exempt`() {
        val resolvers = { listOf("com.google.android.tvlauncher" to 0, "com.android.tv.settings" to -1000) }
        assertEquals(setOf("com.google.android.tvlauncher"), ExemptPackages.homes("android", resolvers))
        assertEquals(setOf("com.google.android.tvlauncher"), ExemptPackages.homes(null, resolvers))
    }
}
