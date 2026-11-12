package dev.tvlimiter.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrambledPadStateTest {

    private fun ScrambledPadState.moveTo(cell: Int) {
        while (cursor != cell) right()
    }

    private fun ScrambledPadState.enter(digit: Int) {
        moveTo(digits.indexOf(digit))
        ok()
    }

    @Test
    fun `pads opened with different seeds have different orders and the seed sets the cursor`() {
        val a = ScrambledPadState(Random(1))
        val b = ScrambledPadState(Random(2))
        assertNotEquals(a.digits, b.digits)
        assertEquals((0..9).toList(), a.digits.sorted())
        val expected = Random(1).let { r -> (0..9).shuffled(r); r.nextInt(10) }
        assertEquals(expected, a.cursor)
    }

    @Test
    fun `OK enters the digit under the cursor, then the cursor moves to another digit cell`() {
        val pad = ScrambledPadState(Random(7))
        repeat(50) {
            val before = pad.cursor
            pad.moveTo(before)
            pad.ok()
            assertNotEquals(before, pad.cursor)
            assertTrue(pad.cursor < ScrambledPadState.DIGIT_CELLS)
            pad.clear()
        }
    }

    @Test
    fun `the display shows one arrow per digit and never a digit`() {
        val pad = ScrambledPadState(Random(3))
        listOf(4, 8, 2).forEach { pad.enter(it) }
        assertEquals("▲▲▲", pad.display)
        assertFalse(pad.display.any { it.isDigit() })
    }

    @Test
    fun `Done submits the entered PIN only from four digits`() {
        val pad = ScrambledPadState(Random(5))
        listOf(1, 2, 3).forEach { pad.enter(it) }
        pad.moveTo(ScrambledPadState.DONE)
        assertNull(pad.ok())
        pad.enter(4)
        pad.moveTo(ScrambledPadState.DONE)
        assertEquals("1234", pad.ok())
    }

    @Test
    fun `a ninth digit is ignored and Delete removes the last digit`() {
        val pad = ScrambledPadState(Random(9))
        repeat(9) { pad.enter(1) }
        assertEquals(8, pad.length)
        pad.moveTo(ScrambledPadState.DELETE)
        pad.ok()
        assertEquals(7, pad.length)
    }

    @Test
    fun `Delete on an empty entry does nothing`() {
        val pad = ScrambledPadState(Random(4))
        pad.moveTo(ScrambledPadState.DELETE)
        pad.ok()
        assertEquals(0, pad.length)
    }

    @Test
    fun `left and right wrap across all twelve cells`() {
        val pad = ScrambledPadState(Random(6))
        pad.moveTo(0)
        pad.left()
        assertEquals(ScrambledPadState.DONE, pad.cursor)
        pad.right()
        assertEquals(0, pad.cursor)
    }

    @Test
    fun `reset clears the entry and reshuffles`() {
        val pad = ScrambledPadState(Random(11))
        val order = pad.digits
        pad.enter(5)
        pad.reset()
        assertEquals(0, pad.length)
        assertNotEquals(order, pad.digits)
    }
}
