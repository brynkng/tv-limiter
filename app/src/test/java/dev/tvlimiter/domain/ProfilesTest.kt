package dev.tvlimiter.domain

import dev.tvlimiter.data.JsonFileStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    private val young = Profile("a1", "Sam", AgeBand.YOUNG)
    private val teen = Profile("b2", "Ava", AgeBand.TEEN)

    @Test
    fun `only young children may skip the passkey`() {
        assertTrue(AgeBand.YOUNG.passkeyOptional)
        assertFalse(AgeBand.OLDER.passkeyOptional)
        assertFalse(AgeBand.TEEN.passkeyOptional)
    }

    @Test
    fun `a young child without a passkey starts at once, with one they enter it`() {
        assertTrue(young.startsWithoutPasskey(hasPasskey = false))
        assertFalse(young.startsWithoutPasskey(hasPasskey = true))
        assertFalse(young.needsPasskey(hasPasskey = false))
    }

    @Test
    fun `an older child always enters a passkey and needs one set`() {
        assertFalse(teen.startsWithoutPasskey(hasPasskey = false))
        assertFalse(teen.startsWithoutPasskey(hasPasskey = true))
        assertTrue(teen.needsPasskey(hasPasskey = false))
        assertFalse(teen.needsPasskey(hasPasskey = true))
    }

    @Test
    fun `the shared profile keeps today's file paths`() {
        val dir = File("/data/files")
        for (name in listOf("limits.json", "usage.json", "history.json", "pin.json")) {
            assertEquals(File(dir, name), Profiles.dataFile(dir, Profiles.SHARED_ID, name))
        }
    }

    @Test
    fun `each child's files go in its own folder`() {
        val dir = File("/data/files")
        assertEquals(File("/data/files/profiles/a1/limits.json"), Profiles.dataFile(dir, "a1", "limits.json"))
        assertEquals(File("/data/files/profiles/b2/pin.json"), Profiles.dataFile(dir, "b2", "pin.json"))
        assertEquals(File("/data/files/profiles/a1"), Profiles.folder(dir, "a1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the shared profile has no folder to delete`() {
        Profiles.folder(File("/data/files"), Profiles.SHARED_ID)
    }

    @Test
    fun `ids that could leave the profiles folder are refused`() {
        assertFalse(Profiles.isValidId("../x"))
        assertFalse(Profiles.isValidId(""))
        assertFalse(Profiles.isValidId("A1"))
        assertTrue(Profiles.isValidId("a1b2"))
    }

    @Test
    fun `a new id skips the shared id and ids in use`() {
        val tries = ArrayDeque(listOf(Profiles.SHARED_ID, "a1", "../x", "c3"))
        assertEquals("c3", Profiles.newId(setOf("a1")) { tries.removeFirst() })
        assertNotEquals(Profiles.SHARED_ID, Profiles.newId(emptySet()))
    }

    @Test
    fun `profiles mode needs a child, and every older child needs a passkey`() {
        assertEquals("Add a profile first.", Profiles.switchProblem(emptyList()) { false })
        assertNull(Profiles.switchProblem(listOf(young)) { false })
        assertEquals("Set a passkey for Ava first.", Profiles.switchProblem(listOf(young, teen)) { false })
        assertNull(Profiles.switchProblem(listOf(young, teen)) { it == "b2" })
    }

    @Test
    fun `adding a profile with the same id replaces it`() {
        val file = ProfilesFile().withProfile(young).withProfile(teen).withProfile(young.copy(name = "Sammy"))
        assertEquals(listOf("Sammy", "Ava"), file.profiles.map { it.name })
        assertEquals(teen, file.find("b2"))
    }

    @Test
    fun `removing the last profile goes back to shared mode`() {
        val file = ProfilesFile(Mode.PROFILES, listOf(young, teen))
        assertEquals(Mode.PROFILES, file.without("a1").mode)
        assertEquals(Mode.SHARED, file.without("a1").without("b2").mode)
    }

    @Test
    fun `a TV without a profiles file is in shared mode`() {
        val store = JsonFileStore(File.createTempFile("profiles", ".json").also { it.delete() }, ProfilesFile.serializer()) { ProfilesFile() }
        assertEquals(ProfilesFile(Mode.SHARED, emptyList()), store.read())
    }
}
