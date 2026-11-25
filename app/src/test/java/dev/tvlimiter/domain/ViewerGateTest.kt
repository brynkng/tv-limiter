package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerGateTest {
    private val kids = Profiles.defaultProfile()
    private val teen = Profile("b2", "Ava", AgeBand.TEEN)
    private val noPasskeys: (String) -> Boolean = { false }

    @Test
    fun `one profile without a passkey is used without asking`() {
        assertEquals(Gate.CountFor(kids.id), ViewerGate.decide(null, parentMode = false, listOf(kids), noPasskeys))
    }

    @Test
    fun `one profile with a passkey asks for it`() {
        assertEquals(Gate.ShowPicker, ViewerGate.decide(null, parentMode = false, listOf(kids)) { true })
        assertEquals(Gate.ShowPicker, ViewerGate.decide(null, parentMode = false, listOf(teen)) { true })
    }

    @Test
    fun `parent mode counts nothing, picked or not`() {
        for (viewer in listOf(null, "b2")) {
            assertEquals(Gate.CountNothing, ViewerGate.decide(viewer, parentMode = true, listOf(kids, teen), noPasskeys))
            assertEquals(Gate.CountNothing, ViewerGate.decide(viewer, parentMode = true, listOf(kids), noPasskeys))
        }
    }

    @Test
    fun `several profiles with nobody picked shows the picker`() {
        assertEquals(Gate.ShowPicker, ViewerGate.decide(null, parentMode = false, listOf(kids, teen), noPasskeys))
    }

    @Test
    fun `counts for whoever picked`() {
        assertEquals(Gate.CountFor("b2"), ViewerGate.decide("b2", parentMode = false, listOf(kids, teen), noPasskeys))
    }

    @Test
    fun `a viewer whose profile was deleted has to pick again`() {
        assertEquals(Gate.ShowPicker, ViewerGate.decide("gone", parentMode = false, listOf(kids, teen), noPasskeys))
    }

    @Test
    fun `the viewer starts empty, is set by a pick, and clears`() {
        val viewer = Viewer()
        assertNull(viewer.current)
        viewer.start("a1")
        assertEquals("a1", viewer.current)
        viewer.end()
        assertNull(viewer.current)
    }
}
