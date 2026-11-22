package dev.tvlimiter.ui

import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.GrantChoice
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.LimitEvaluator
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.PinGuard
import java.time.DayOfWeek

enum class BlockStep { CHOOSE, PIN, GRANT, DONE }

/**
 * Drives the block screen: name the limit that ran out, then go home or enter the PIN and
 * pick how much time to add. When two limits ran out together, the owner moves straight on to
 * the second one's grant menu without typing the PIN again. Each grant still goes only to the
 * limit it was picked for.
 */
class BlockViewModel(
    private val blockedPackage: String,
    private val limits: () -> List<Limit>,
    private val usage: () -> DayUsage,
    private val weekday: () -> DayOfWeek,
    private val recordGrant: (limitId: String, choice: GrantChoice) -> Unit,
    private val guard: PinGuard,
    private val appLabel: (packageName: String) -> String,
) {
    var firing: Limit? = null
        private set
    var step: BlockStep = BlockStep.CHOOSE
        private set

    init {
        refresh()
        if (firing == null) step = BlockStep.DONE
    }

    val headline: String
        get() = when (val limit = firing) {
            null -> ""
            else -> if (limit.isWholeTv) "TV time is done for today" else "${appLabel(blockedPackage)} is done for today"
        }

    /** The limit's own name, used on the grant menu. */
    val limitName: String
        get() = firing?.let { if (it.isWholeTv) "TV time" else appLabel(blockedPackage) } ?: ""

    fun requestPin() {
        if (step == BlockStep.CHOOSE) step = BlockStep.PIN
    }

    fun cancelPin() {
        if (step == BlockStep.PIN) step = BlockStep.CHOOSE
    }

    fun submitPin(pin: String): PinCheck = checkPin(pin).also { applyCheck(it) }

    /** The slow part, safe to run off the main thread: it only reads and writes the passkey file. */
    fun checkPin(pin: String): PinCheck = guard.check(pin)

    fun applyCheck(check: PinCheck) {
        if (check == PinCheck.Ok && step == BlockStep.PIN) step = BlockStep.GRANT
    }

    fun grant(choice: GrantChoice) {
        if (step != BlockStep.GRANT) return
        val limit = firing ?: return
        recordGrant(limit.id, choice)
        refresh()
        step = if (firing == null) BlockStep.DONE else BlockStep.GRANT
    }

    private fun refresh() {
        firing = LimitEvaluator.evaluate(limits(), usage(), weekday(), blockedPackage).blocking?.limit
    }
}
