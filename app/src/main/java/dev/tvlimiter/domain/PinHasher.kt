package dev.tvlimiter.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** PBKDF2-HMAC-SHA256 with a random salt. Only the salt and the hash are ever stored. */
class PinHasher(val iterations: Int = DEFAULT_ITERATIONS) {

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /**
     * PBKDF2-HMAC-SHA256 for one 32-byte block, written out over [Mac]. Android's own
     * "PBKDF2WithHmacSHA256" is pure Java and takes seconds on a TV, while HmacSHA256 runs in
     * native code. The output is the same, so passkeys saved either way still match.
     */
    fun hash(pin: String, salt: ByteArray, iterations: Int = this.iterations): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pin.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var block = mac.doFinal()
        val result = block.copyOf()
        repeat(iterations - 1) {
            block = mac.doFinal(block)
            for (i in result.indices) result[i] = (result[i].toInt() xor block[i].toInt()).toByte()
        }
        return result
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
        /**
         * Kept low enough that checking feels instant on a TV's slow processor. A 6-arrow passkey
         * has only 4,096 possible codes, so more rounds wouldn't stop anyone who had the file;
         * the lockout and the app's private storage are what protect it.
         */
        const val DEFAULT_ITERATIONS = 10_000
        private const val SALT_BYTES = 16

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
        private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
