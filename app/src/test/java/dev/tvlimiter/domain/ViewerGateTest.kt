package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerGateTest {
    private val ids = listOf("a1", "b2")

    @Test
    fun `shared mode counts for the shared profile, picked or not`() {
        assertEquals(Gate.CountFor(Profiles.SHARED_ID), ViewerGate.decide(Mode.SHARED, null, parentMode = false, ids))
        assertEquals(Gate.CountFor(Profiles.SHARED_ID), ViewerGate.decide(Mode.SHARED, "a1", parentMode = false, ids))
    }

    @Test
    fun `parent mode counts nothing in either mode`() {
        for (mode in Mode.entries) {
            for (viewer in listOf(null, "a1")) {
                assertEquals(Gate.CountNothing, ViewerGate.decide(mode, viewer, parentMode = true, ids))
            }
        }
    }

    @Test
    fun `profiles mode with nobody picked shows the picker`() {
        assertEquals(Gate.ShowPicker, ViewerGate.decide(Mode.PROFILES, null, parentMode = false, ids))
    }

    @Test
    fun `profiles mode counts for whoever picked`() {
        assertEquals(Gate.CountFor("b2"), ViewerGate.decide(Mode.PROFILES, "b2", parentMode = false, ids))
    }

    @Test
    fun `a viewer whose profile was deleted has to pick again`() {
        assertEquals(Gate.ShowPicker, ViewerGate.decide(Mode.PROFILES, "gone", parentMode = false, ids))
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
