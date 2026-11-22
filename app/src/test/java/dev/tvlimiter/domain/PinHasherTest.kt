package dev.tvlimiter.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher(iterations = 1_000)

    @Test
    fun `the correct PIN verifies and a wrong one does not`() {
        val record = hasher.create("4821")
        assertTrue(hasher.verify("4821", record))
        assertFalse(hasher.verify("4822", record))
    }

    @Test
    fun `the same PIN with two salts gives different hashes`() {
        assertNotEquals(hasher.create("4821").hash, hasher.create("4821").hash)
    }

    @Test
    fun `the default uses the full iteration count`() {
        assertTrue(PinHasher().create("1234").iterations == PinHasher.DEFAULT_ITERATIONS)
    }

    @Test
    fun `the hash matches the platform PBKDF2, so saved passkeys still check out`() {
        val salt = ByteArray(16) { it.toByte() }
        val spec = javax.crypto.spec.PBEKeySpec("UUDDLR".toCharArray(), salt, 1_000, 256)
        val expected = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        assertTrue(expected.contentEquals(hasher.hash("UUDDLR", salt, 1_000)))
    }
}
