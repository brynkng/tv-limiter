package dev.tvlimiter.ui

import dev.tvlimiter.domain.AgeBand
import dev.tvlimiter.domain.InMemoryPinStore
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.PinHasher
import dev.tvlimiter.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoViewModelTest {
    private val hasher = PinHasher(iterations = 1_000)
    private val young = Profile("a1", "Sam", AgeBand.YOUNG)
    private val teen = Profile("b2", "Ava", AgeBand.TEEN)
    private val guards = mutableMapOf<String, PinGuard>()
    private val parent = newGuard().apply { setPin("UUUUUU") }
    private var viewer: String? = null
    private var parentMode = false

    private fun newGuard() = PinGuard(InMemoryPinStore(), hasher) { 0L }

    private fun guard(id: String) = guards.getOrPut(id) { newGuard() }

    private fun model() = WhoViewModel(
        profiles = listOf(young, teen),
        hasPasskey = { guard(it).hasPin() },
        childGuard = ::guard,
        parentGuard = parent,
        startViewer = { viewer = it },
        startParentMode = { parentMode = true },
    )

    @Test
    fun `a young child without a passkey starts at once`() {
        val model = model()
        model.pick(WhoChoice.Child(young))
        assertEquals(WhoStep.DONE, model.step)
        assertEquals("a1", viewer)
    }

    @Test
    fun `a young child with a passkey enters it`() {
        guard("a1").setPin("LLLLLL")
        val model = model()
        model.pick(WhoChoice.Child(young))
        assertEquals(WhoStep.PIN, model.step)
        assertNull(viewer)
    }

    @Test
    fun `a teen needs their own passkey, not the parent's`() {
        guard("b2").setPin("DDDDDD")
        val model = model()
        model.pick(WhoChoice.Child(teen))
        assertEquals("Ava, enter your passkey", model.title)

        val wrong = model.checkPin("UUUUUU")
        model.applyCheck(wrong)
        assertTrue(wrong is PinCheck.Wrong)
        assertEquals(WhoStep.PIN, model.step)

        model.applyCheck(model.checkPin("DDDDDD"))
        assertEquals(WhoStep.DONE, model.step)
        assertEquals("b2", viewer)
        assertFalse(parentMode)
    }

    @Test
    fun `a teen with no passkey set can't start`() {
        val model = model()
        model.pick(WhoChoice.Child(teen))
        assertEquals(PinCheck.NoPin, model.checkPin("DDDDDD"))
        assertEquals(WhoStep.PIN, model.step)
        assertNull(viewer)
    }

    @Test
    fun `the parent tile takes the parent passkey and starts parent mode`() {
        val model = model()
        model.pick(WhoChoice.Parent)
        assertEquals("Parent passkey", model.title)
        model.applyCheck(model.checkPin("UUUUUU"))
        assertEquals(WhoStep.DONE, model.step)
        assertTrue(parentMode)
        assertNull(viewer)
    }

    @Test
    fun `backing out of a passkey returns to the tiles`() {
        val model = model()
        model.pick(WhoChoice.Parent)
        model.cancelPin()
        assertEquals(WhoStep.CHOOSE, model.step)
        assertEquals("Who's watching?", model.title)
        assertEquals(0, model.lockoutSecondsLeft())
    }

    @Test
    fun `each passkey has its own lockout`() {
        guard("b2").setPin("DDDDDD")
        val model = model()
        model.pick(WhoChoice.Child(teen))
        repeat(PinGuard.MAX_FAILURES) { model.checkPin("LLLLLL") }
        assertEquals(PinGuard.LOCKOUT_SECONDS, model.lockoutSecondsLeft())
        model.cancelPin()
        model.pick(WhoChoice.Parent)
        assertEquals(0, model.lockoutSecondsLeft())
    }
}
