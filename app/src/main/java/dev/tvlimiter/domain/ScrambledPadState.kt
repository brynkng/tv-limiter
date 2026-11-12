package dev.tvlimiter.domain

import kotlin.random.Random

/**
 * State for the masked PIN pad. A row of the ten digits in shuffled order, followed by fixed
 * Delete and Done cells. After each digit is entered the cursor jumps to a random other digit,
 * so the remote presses don't reveal which digit was picked. The entry field only ever shows
 * one arrow per digit.
 */
class ScrambledPadState(private val random: Random = Random.Default) {
    var digits: List<Int> = shuffledDigits()
        private set
    var cursor: Int = random.nextInt(DIGIT_CELLS)
        private set
    private val entered = mutableListOf<Int>()

    val length: Int get() = entered.size
    val canSubmit: Boolean get() = entered.size >= PinGuard.MIN_LENGTH
    val display: String get() = ARROW.repeat(entered.size)

    fun label(cell: Int): String = when (cell) {
        DELETE -> "Delete"
        DONE -> "Done"
        else -> digits[cell].toString()
    }

    fun left() {
        cursor = (cursor - 1 + CELL_COUNT) % CELL_COUNT
    }

    fun right() {
        cursor = (cursor + 1) % CELL_COUNT
    }

    /** Presses OK on the cell under the cursor. Returns the PIN when Done submits it. */
    fun ok(): String? {
        when (cursor) {
            DELETE -> if (entered.isNotEmpty()) entered.removeAt(entered.lastIndex)
            DONE -> if (canSubmit) return entered.joinToString("")
            else -> {
                if (entered.size < PinGuard.MAX_LENGTH) entered += digits[cursor]
                jumpFrom(cursor)
            }
        }
        return null
    }

    fun clear() = entered.clear()

    /** Clears the entry and starts over with a new order and cursor, as after a wrong PIN. */
    fun reset() {
        entered.clear()
        digits = shuffledDigits()
        cursor = random.nextInt(DIGIT_CELLS)
    }

    private fun jumpFrom(from: Int) {
        var next = random.nextInt(DIGIT_CELLS - 1)
        if (next >= from) next++
        cursor = next
    }

    private fun shuffledDigits() = (0..9).shuffled(random)

    companion object {
        const val DIGIT_CELLS = 10
        const val DELETE = 10
        const val DONE = 11
        const val CELL_COUNT = 12
        const val ARROW = "▲"
    }
}
