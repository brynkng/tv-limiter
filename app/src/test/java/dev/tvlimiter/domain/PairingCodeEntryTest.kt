package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeEntryTest {
    private fun typed(text: String) = PairingCodeEntry().also { e -> text.forEach { e.press(it.digitToInt()) } }

    @Test
    fun `digits are kept in order until the code is complete`() {
        val entry = typed("1234")
        assertEquals("1234", entry.text)
        assertFalse(entry.isComplete)
        assertEquals(null, entry.digitAt(4))
        assertEquals('3', entry.digitAt(2))
    }

    @Test
    fun `the code is complete at 8 digits and takes no more`() {
        val entry = typed("12345678")
        assertTrue(entry.isComplete)
        assertFalse(entry.press(9))
        assertEquals("12345678", entry.text)
    }

    @Test
    fun `delete removes the last digit and does nothing when empty`() {
        val entry = typed("12")
        entry.delete()
        assertEquals("1", entry.text)
        entry.delete()
        entry.delete()
        assertEquals("", entry.text)
    }

    @Test
    fun `leading zeros are kept`() {
        assertEquals("00012345", typed("00012345").text)
    }
}
