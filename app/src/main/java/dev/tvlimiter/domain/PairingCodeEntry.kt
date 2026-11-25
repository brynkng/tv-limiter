package dev.tvlimiter.domain

/** The pairing code being typed on the on-screen number grid. TubeGuard's codes are [LENGTH] digits. */
class PairingCodeEntry {
    private val typed = StringBuilder()

    val text: String get() = typed.toString()
    val length: Int get() = typed.length
    val isComplete: Boolean get() = typed.length == LENGTH

    /** False when the code is already complete, so a digit never replaces one silently. */
    fun press(digit: Int): Boolean {
        if (digit !in 0..9 || isComplete) return false
        typed.append(digit)
        return true
    }

    fun delete() {
        if (typed.isNotEmpty()) typed.setLength(typed.length - 1)
    }

    fun clear() = typed.setLength(0)

    /** Digit [index] for the boxes on screen, or null while it is still empty. */
    fun digitAt(index: Int): Char? = typed.getOrNull(index)

    companion object {
        const val LENGTH = 8

        /** The first digits find a code on the backend, and wrong guesses are counted against the code they find. */
        const val LOOKUP_LENGTH = 4
    }
}
