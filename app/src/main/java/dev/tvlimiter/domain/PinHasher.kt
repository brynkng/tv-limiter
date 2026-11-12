package dev.tvlimiter.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 with a random salt. Only the salt and the hash are ever stored. */
class PinHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = this.iterations): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun create(pin: String): PinRecord {
        val salt = newSalt()
        return PinRecord(salt = salt.toHex(), hash = hash(pin, salt).toHex(), iterations = iterations)
    }

    fun verify(pin: String, record: PinRecord): Boolean {
        val actual = hash(pin, record.salt.fromHex(), record.iterations)
        return MessageDigest.isEqual(actual, record.hash.fromHex())
    }

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
        private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
