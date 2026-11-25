package dev.tvlimiter.ui

import dev.tvlimiter.domain.PairResult
import dev.tvlimiter.domain.TubeGuardChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingViewModelTest {
    private val model = PairingViewModel()

    private fun type(digits: String) = digits.forEach { model.press(it.digitToInt()) }

    private val problem get() = (model.status as PairingViewModel.Status.Problem).message

    @Test
    fun `the code is checked once all 8 digits are in`() {
        type("1234567")
        assertNull(model.beginCheck())
        type("8")
        assertEquals("12345678", model.beginCheck())
        assertEquals(PairingViewModel.Status.Checking, model.status)
    }

    @Test
    fun `keys do nothing while a check runs`() {
        type("12345678")
        model.beginCheck()
        model.press(9)
        model.delete()
        assertEquals("12345678", model.entry.text)
        assertNull(model.beginCheck())
    }

    @Test
    fun `a paired code names the children`() {
        type("12345678")
        model.beginCheck()
        model.finishCheck(PairResult.Paired("d", "t", listOf(TubeGuardChild("c1", "Ana"), TubeGuardChild("c2", "Ben"))))
        assertEquals(PairingViewModel.Status.Connected(listOf("Ana", "Ben")), model.status)
        assertEquals("Ana and Ben", PairingViewModel.names(listOf("Ana", "Ben")))
        assertEquals("Ana, Ben and Cy", PairingViewModel.names(listOf("Ana", "Ben", "Cy")))
        assertEquals("Ana", PairingViewModel.names(listOf("Ana")))
    }

    @Test
    fun `a wrong code keeps the digits so a typo can be fixed`() {
        type("12345678")
        model.beginCheck()
        model.finishCheck(PairResult.InvalidCode)
        assertTrue(problem.contains("didn't work"))
        assertEquals("12345678", model.entry.text)
        model.delete()
        type("9")
        assertEquals("12345679", model.beginCheck())
    }

    @Test
    fun `a used, expired or over-tried code asks for a new code and clears the digits`() {
        for ((result, words) in listOf(
            PairResult.CodeUsed to "already used",
            PairResult.CodeExpired to "expired",
            PairResult.TooManyAttempts to "too many times",
        )) {
            type("12345678")
            model.beginCheck()
            model.finishCheck(result)
            assertTrue(problem, problem.contains(words))
            assertTrue(problem, problem.contains("new code"))
            assertEquals("the digits are cleared", "", model.entry.text)
        }
    }

    @Test
    fun `a rate limit and an unreachable backend each have their own message`() {
        type("12345678")
        model.beginCheck()
        model.finishCheck(PairResult.RateLimited)
        assertTrue(problem.contains("15 minutes"))

        type("12345678")
        model.beginCheck()
        model.finishCheck(PairResult.Unreachable)
        assertTrue(problem.contains("internet"))
        assertEquals("12345678", model.entry.text)
    }

    @Test
    fun `typing a ninth digit says how to fix a mistake`() {
        type("12345678")
        model.press(9)
        assertTrue(problem.contains("Delete"))
        assertEquals("12345678", model.entry.text)
    }
}
