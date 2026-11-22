package dev.tvlimiter.domain

/** One of the four arrow buttons on the remote. Each has a letter used when the code is saved. */
enum class Arrow(val letter: Char) { UP('U'), DOWN('D'), LEFT('L'), RIGHT('R') }

/**
 * A code made of arrow presses on the remote, like a game console passkey. Nothing about the
 * presses is kept for display: the screen only learns how many have been entered, so someone
 * watching the TV can't tell which arrows were pressed. The code is submitted as soon as the
 * last press is entered.
 */
class PasskeyState {
    private val entered = StringBuilder()

    val length: Int get() = entered.length

    /** Adds one press. Returns the finished code when this press completes it. */
    fun press(arrow: Arrow): String? {
        entered.append(arrow.letter)
        if (entered.length < LENGTH) return null
        val code = entered.toString()
        entered.clear()
        return code
    }

    fun clear() = entered.clear()

    companion object {
        const val LENGTH = 6
    }
}
