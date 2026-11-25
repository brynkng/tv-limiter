package dev.tvlimiter.data

import dev.tvlimiter.domain.AgeBand
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.Profiles
import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.ProfilesFile
import dev.tvlimiter.domain.TubeGuardChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store() = JsonFileStore(java.io.File(folder.root, "profiles.json"), ProfilesFile.serializer()) { ProfilesFile() }

    @Test
    fun `profiles are saved and read back after the default one`() {
        val repo = ProfilesRepository(store())
        repo.upsert(Profile("a1", "Sam", AgeBand.YOUNG))

        val reread = ProfilesRepository(store())
        assertEquals(listOf("Kids", "Sam"), reread.profiles().map { it.name })
    }

    @Test
    fun `a profile's TubeGuard link is saved and read back`() {
        val repo = ProfilesRepository(store())
        repo.upsert(Profile("a1", "Sam", AgeBand.YOUNG, tubeGuardChildId = "c1", linkChosen = true))
        val sam = ProfilesRepository(store()).find("a1")!!
        assertEquals("c1", sam.tubeGuardChildId)
        assertTrue(sam.linkChosen)
    }

    @Test
    fun `profiles saved before links existed read as not linked`() {
        java.io.File(folder.root, "profiles.json").writeText("""{"profiles":[{"id":"shared","name":"Kids","band":"YOUNG"}]}""")
        val kids = ProfilesRepository(store()).find(Profiles.DEFAULT_ID)!!
        assertNull(kids.tubeGuardChildId)
        assertFalse(kids.linkChosen)
    }

    @Test
    fun `the only profile links to the only child, once`() {
        val repo = ProfilesRepository(store())
        assertTrue(repo.autoLink(listOf(TubeGuardChild("c1", "Ana"))))
        assertEquals("c1", ProfilesRepository(store()).find(Profiles.DEFAULT_ID)!!.tubeGuardChildId)
        assertFalse("nothing changes the second time", repo.autoLink(listOf(TubeGuardChild("c1", "Ana"))))
    }

    @Test
    fun `no automatic link with two children, or after a parent chose Not linked`() {
        val repo = ProfilesRepository(store())
        assertFalse(repo.autoLink(listOf(TubeGuardChild("c1", "Ana"), TubeGuardChild("c2", "Ben"))))
        repo.upsert(repo.find(Profiles.DEFAULT_ID)!!.copy(linkChosen = true))
        assertFalse(repo.autoLink(listOf(TubeGuardChild("c1", "Ana"))))
        assertNull(repo.find(Profiles.DEFAULT_ID)!!.tubeGuardChildId)
    }

    @Test
    fun `the default profile stays when others are removed`() {
        val repo = ProfilesRepository(store())
        repo.upsert(Profile("a1", "Sam", AgeBand.YOUNG))
        repo.remove("a1")
        repo.remove(Profiles.DEFAULT_ID)
        assertEquals(listOf(Profiles.DEFAULT_ID), ProfilesRepository(store()).profiles().map { it.id })
    }

    @Test
    fun `a new profile's limits can start as a copy of the shared limits`() {
        val shared = LimitsRepository(JsonFileStore(java.io.File(folder.root, "limits.json"), LimitsFile.serializer()) { LimitsFile() })
        shared.upsert(Limit.newAppLimit("yt"))
        val childStore = JsonFileStore(java.io.File(folder.root, "profiles/a1/limits.json"), LimitsFile.serializer()) { LimitsFile() }
        LimitsRepository(childStore).replaceAll(shared.all())

        assertEquals(shared.all(), LimitsRepository(childStore).all())
        // Editing the copy leaves the shared limits alone.
        LimitsRepository(childStore).delete(Limit.appLimitId("yt"))
        assertEquals(2, shared.all().size)
    }
}
