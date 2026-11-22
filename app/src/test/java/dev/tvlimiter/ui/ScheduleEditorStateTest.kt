package dev.tvlimiter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleEditorStateTest {
    private fun editor(vararg minutes: Int?) = ScheduleEditorState(minutes.toList())

    @Test
    fun `right from 45 gives 60`() {
        val state = editor(45, 0, 0, 0, 0, 0, 0)
        state.right(0)
        assertEquals(60, state.minutes[0])
    }

    @Test
    fun `left from 0 gives no limit and left again stays there`() {
        val state = editor(0, 0, 0, 0, 0, 0, 0)
        state.left(0)
        assertNull(state.minutes[0])
        state.left(0)
        assertNull(state.minutes[0])
    }

    @Test
    fun `right from no limit gives 15`() {
        val state = editor(null, 0, 0, 0, 0, 0, 0)
        state.right(0)
        assertEquals(15, state.minutes[0])
    }

    @Test
    fun `the maximum is 24 hours`() {
        val state = editor(24 * 60, 0, 0, 0, 0, 0, 0)
        state.right(0)
        assertEquals(24 * 60, state.minutes[0])
    }

    @Test
    fun `copy Monday to weekdays sets Tuesday to Friday and leaves the weekend`() {
        val state = editor(90, 10, 20, 30, 40, null, 120)
        state.copyMondayToWeekdays()
        assertEquals(listOf(90, 90, 90, 90, 90, null, 120), state.minutes)
    }

    @Test
    fun `copy Saturday to weekend sets Sunday only`() {
        val state = editor(1, 2, 3, 4, 5, 240, 7)
        state.copySaturdayToWeekend()
        assertEquals(listOf(1, 2, 3, 4, 5, 240, 240), state.minutes)
    }

    @Test
    fun `copy Monday to every day sets all seven days, including no limit`() {
        val state = editor(null, 10, 20, 30, 40, 50, 60)
        state.copyMondayToAllDays()
        assertEquals(List(7) { null }, state.minutes)
        state.right(0)
        state.copyMondayToAllDays()
        assertEquals(List(7) { 15 }, state.minutes)
    }
}
