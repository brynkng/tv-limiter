package dev.tvlimiter.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUnlockTest {
    @Test
    fun `going to the background locks settings again`() {
        val unlock = SettingsUnlock()
        unlock.onStarted()
        unlock.unlock()
        assertTrue(unlock.isUnlocked)
        unlock.onStopped()
        unlock.onStarted()
        assertFalse(unlock.isUnlocked)
    }

    @Test
    fun `moving between settings screens keeps the unlock`() {
        val unlock = SettingsUnlock()
        unlock.onStarted()
        unlock.unlock()
        unlock.onStarted() // the next screen starts before the previous one stops
        unlock.onStopped()
        assertTrue(unlock.isUnlocked)
    }
}
