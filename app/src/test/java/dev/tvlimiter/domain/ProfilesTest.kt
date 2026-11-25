package dev.tvlimiter.domain

import dev.tvlimiter.data.JsonFileStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `the default profile keeps today's file paths`() {
        val dir = File("/data/files")
        for (name in listOf("limits.json", "usage.json", "history.json", "pin.json")) {
            assertEquals(File(dir, name), Profiles.dataFile(dir, Profiles.DEFAULT_ID, name))
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
    fun `the default profile has no folder to delete`() {
        Profiles.folder(File("/data/files"), Profiles.DEFAULT_ID)
    }

    @Test
    fun `ids that could leave the profiles folder are refused`() {
        assertFalse(Profiles.isValidId("../x"))
        assertFalse(Profiles.isValidId(""))
        assertFalse(Profiles.isValidId("A1"))
        assertTrue(Profiles.isValidId("a1b2"))
    }

    @Test
    fun `a new id skips the default id and ids in use`() {
        val tries = ArrayDeque(listOf(Profiles.DEFAULT_ID, "a1", "../x", "c3"))
        assertEquals("c3", Profiles.newId(setOf("a1")) { tries.removeFirst() })
        assertNotEquals(Profiles.DEFAULT_ID, Profiles.newId(emptySet()))
    }

    @Test
    fun `only a single profile with no passkey to enter skips the picker`() {
        val kids = Profiles.defaultProfile()
        assertFalse(Profiles.needsPicker(listOf(kids)) { false })
        assertTrue(Profiles.needsPicker(listOf(kids)) { true })
        assertTrue(Profiles.needsPicker(listOf(teen)) { true })
        assertTrue(Profiles.needsPicker(listOf(kids, young)) { false })
    }

    @Test
    fun `the default profile's passkey is kept apart from the parent's`() {
        val dir = File("/data/files")
        assertEquals(File(dir, "profile-pin.json"), Profiles.passkeyFile(dir, Profiles.DEFAULT_ID))
        assertNotEquals(File(dir, "pin.json"), Profiles.passkeyFile(dir, Profiles.DEFAULT_ID))
        assertEquals(File("/data/files/profiles/b2/pin.json"), Profiles.passkeyFile(dir, "b2"))
    }

    @Test
    fun `adding a profile with the same id replaces it`() {
        val file = ProfilesFile().withProfile(young).withProfile(teen).withProfile(young.copy(name = "Sammy"))
        assertEquals(listOf("Sammy", "Ava"), file.profiles.map { it.name })
        assertEquals(teen, file.find("b2"))
    }

    @Test
    fun `possessive names drop the extra s after a final s`() {
        assertEquals("Ava's", teen.possessive)
        assertEquals("Kids'", Profiles.defaultProfile().possessive)
    }

    @Test
    fun `the default profile is never removed`() {
        val file = ProfilesFile().withDefault().withProfile(teen)
        assertEquals(listOf(Profiles.DEFAULT_ID), file.without("b2").without(Profiles.DEFAULT_ID).profiles.map { it.id })
    }

    @Test
    fun `a TV without a profiles file has just the default profile, Kids, young`() {
        val store = JsonFileStore(File.createTempFile("profiles", ".json").also { it.delete() }, ProfilesFile.serializer()) { ProfilesFile() }
        assertEquals(listOf(Profile(Profiles.DEFAULT_ID, "Kids", AgeBand.YOUNG)), store.read().withDefault().profiles)
    }

    @Test
    fun `a file saved while modes existed still reads, with the default added first`() {
        val file = File.createTempFile("profiles", ".json")
        file.writeText("""{"mode":"PROFILES","profiles":[{"id":"b2","name":"Ava","band":"TEEN"}]}""")
        val store = JsonFileStore(file, ProfilesFile.serializer()) { ProfilesFile() }
        assertEquals(listOf(Profiles.DEFAULT_ID, "b2"), store.read().withDefault().profiles.map { it.id })
    }
}
