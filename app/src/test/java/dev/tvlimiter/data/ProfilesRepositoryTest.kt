package dev.tvlimiter.data

import dev.tvlimiter.domain.AgeBand
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.Mode
import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.ProfilesFile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store() = JsonFileStore(java.io.File(folder.root, "profiles.json"), ProfilesFile.serializer()) { ProfilesFile() }

    @Test
    fun `profiles and the mode are saved and read back`() {
        val repo = ProfilesRepository(store())
        repo.upsert(Profile("a1", "Sam", AgeBand.YOUNG))
        repo.setMode(Mode.PROFILES)

        val reread = ProfilesRepository(store())
        assertEquals(Mode.PROFILES, reread.mode())
        assertEquals(listOf("Sam"), reread.profiles().map { it.name })
    }

    @Test
    fun `removing the last profile turns profiles mode off`() {
        val repo = ProfilesRepository(store())
        repo.upsert(Profile("a1", "Sam", AgeBand.YOUNG))
        repo.setMode(Mode.PROFILES)
        repo.remove("a1")
        assertEquals(Mode.SHARED, ProfilesRepository(store()).mode())
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
