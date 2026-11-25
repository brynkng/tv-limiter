package dev.tvlimiter.ui

import dev.tvlimiter.domain.AgeBand
import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.TubeGuardChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileChildChoiceTest {
    private val ana = TubeGuardChild("c1", "Ana")
    private val ben = TubeGuardChild("c2", "Ben")
    private val sam = Profile("a1", "Sam", AgeBand.OLDER)

    private fun labels(choice: ProfileChildChoice) = choice.options.map { (if (it.selected) "*" else "") + it.label }

    @Test
    fun `it is hidden while the TV is not connected to TubeGuard`() {
        assertFalse(ProfileChildChoice(sam, listOf(ana), connected = false).visible)
        assertTrue(ProfileChildChoice(sam, listOf(ana), connected = true).visible)
    }

    @Test
    fun `a profile with no link shows Not linked chosen, then the children`() {
        assertEquals(listOf("*Not linked", "Ana", "Ben"), labels(ProfileChildChoice(sam, listOf(ana, ben), connected = true)))
    }

    @Test
    fun `the linked child is the one chosen`() {
        val choice = ProfileChildChoice(sam.copy(tubeGuardChildId = "c2"), listOf(ana, ben), connected = true)
        assertEquals(listOf("Not linked", "Ana", "*Ben"), labels(choice))
        assertEquals("Videos Sam watches are reported for Ben, and Ben's TubeGuard settings apply.", choice.note)
    }

    @Test
    fun `a child a parent removed shows as Not linked`() {
        val choice = ProfileChildChoice(sam.copy(tubeGuardChildId = "gone"), listOf(ana), connected = true)
        assertEquals(listOf("*Not linked", "Ana"), labels(choice))
    }

    @Test
    fun `picking saves the child and marks the choice as made`() {
        val choice = ProfileChildChoice(sam, listOf(ana, ben), connected = true)
        val picked = choice.pick("c1")
        assertEquals("c1", picked.tubeGuardChildId)
        assertTrue(picked.linkChosen)
        val cleared = ProfileChildChoice(picked, listOf(ana, ben), connected = true).pick(null)
        assertNull(cleared.tubeGuardChildId)
        assertTrue("Not linked stays chosen, so no automatic link comes back", cleared.linkChosen)
    }

    @Test
    fun `the note says so when TubeGuard has not listed the children yet`() {
        assertEquals(listOf("*Not linked"), labels(ProfileChildChoice(sam, emptyList(), connected = true)))
        assertTrue(ProfileChildChoice(sam, emptyList(), connected = true).note.contains("hasn't listed"))
    }
}
