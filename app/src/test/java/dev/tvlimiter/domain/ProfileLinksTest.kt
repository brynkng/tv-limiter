package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileLinksTest {
    private val ana = TubeGuardChild("c1", "Ana")
    private val ben = TubeGuardChild("c2", "Ben")
    private val kids = Profiles.defaultProfile()
    private val sam = Profile("a1", "Sam", AgeBand.OLDER, tubeGuardChildId = "c2", linkChosen = true)

    @Test
    fun `a profile reports for the child it is linked to`() {
        assertEquals(ben, ProfileLinks.linkedChild(sam, listOf(ana, ben)))
        assertEquals("c2", ProfileLinks.childIdFor("a1", listOf(kids, sam), listOf(ana, ben)))
    }

    @Test
    fun `a profile with no link reports for no child`() {
        assertNull(ProfileLinks.childIdFor(Profiles.DEFAULT_ID, listOf(kids, sam), listOf(ana, ben)))
    }

    @Test
    fun `no profile active reports for no child`() {
        assertNull(ProfileLinks.childIdFor(null, listOf(kids, sam), listOf(ana, ben)))
        assertNull(ProfileLinks.childIdFor("gone", listOf(kids, sam), listOf(ana, ben)))
    }

    @Test
    fun `a child removed from the TV leaves the profile not linked`() {
        assertNull(ProfileLinks.linkedChild(sam, listOf(ana)))
        assertNull(ProfileLinks.childIdFor("a1", listOf(kids, sam), listOf(ana)))
    }

    @Test
    fun `the only profile links to the only child`() {
        assertEquals(kids.copy(tubeGuardChildId = "c1"), ProfileLinks.autoLinked(listOf(kids), listOf(ana)))
    }

    @Test
    fun `no automatic link with several profiles, several children, or a choice already made`() {
        assertNull(ProfileLinks.autoLinked(listOf(kids, sam), listOf(ana)))
        assertNull(ProfileLinks.autoLinked(listOf(kids), listOf(ana, ben)))
        assertNull(ProfileLinks.autoLinked(listOf(kids), emptyList()))
        assertNull(ProfileLinks.autoLinked(listOf(kids.copy(linkChosen = true)), listOf(ana)))
        assertNull(ProfileLinks.autoLinked(listOf(kids.copy(tubeGuardChildId = "c1")), listOf(ana)))
    }

    @Test
    fun `the only profile links again to the only child when its earlier child was removed`() {
        val relinked = ProfileLinks.autoLinked(listOf(kids.copy(tubeGuardChildId = "gone")), listOf(ben))
        assertEquals("c2", relinked?.tubeGuardChildId)
    }
}
