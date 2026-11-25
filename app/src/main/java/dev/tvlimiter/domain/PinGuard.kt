package dev.tvlimiter.domain

import kotlinx.serialization.Serializable

/**
 * The stored PIN plus the failure count and lockout. The lockout lives here, not in memory,
 * so leaving the screen or switching to another PIN prompt doesn't reset it.
 */
@Serializable
data class PinRecord(
    val salt: String,
    val hash: String,
    val iterations: Int,
    val failures: Int = 0,
    /** Elapsed-realtime millis when the current lockout began, or null when not locked. */
    val lockStartedAt: Long? = null,
)

interface PinStore {
    fun load(): PinRecord?
    fun save(record: PinRecord)

    /** Removes the saved PIN, so none is set. */
    fun clear()

    /** True when the saved PIN could not be read back, so it must not be treated as never set. */
    fun isDamaged(): Boolean = false
}

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val triesLeft: Int) : PinCheck
    data class LockedOut(val secondsLeft: Int) : PinCheck
    data object NoPin : PinCheck
    data object Damaged : PinCheck
}

/**
 * Checks PIN attempts and locks entry for a minute after five wrong PINs in a row.
 * Time comes from a monotonic clock, so changing the wall clock can't end a lockout.
 */
class PinGuard(
    private val store: PinStore,
    private val hasher: PinHasher,
    private val elapsedMillis: () -> Long,
) {
    fun hasPin(): Boolean = store.load() != null

    /** No PIN can be read, but one was set before; setup must not be offered to whoever holds the remote. */
    fun isDamaged(): Boolean = store.load() == null && store.isDamaged()

    fun setPin(pin: String) = store.save(hasher.create(pin))

    /** Removes the PIN. Only a young child's profile, where a passkey is optional, offers this. */
    fun clearPin() = store.clear()

    fun lockoutSecondsLeft(): Int {
        val record = store.load() ?: return 0
        val started = record.lockStartedAt ?: return 0
        val now = elapsedMillis()
        if (now < started) {
            // The monotonic clock restarted, so the device rebooted. Start the minute again.
            store.save(record.copy(lockStartedAt = now))
            return LOCKOUT_SECONDS
        }
        val leftMillis = started + LOCKOUT_SECONDS * 1000L - now
        if (leftMillis <= 0) return 0
        return ((leftMillis + 999) / 1000).toInt()
    }

    fun check(pin: String): PinCheck {
        if (store.load() == null) return if (store.isDamaged()) PinCheck.Damaged else PinCheck.NoPin
        val locked = lockoutSecondsLeft()
        if (locked > 0) return PinCheck.LockedOut(locked)
        val record = store.load() ?: return PinCheck.NoPin
        val fresh = if (record.lockStartedAt != null) record.copy(failures = 0, lockStartedAt = null) else record
        if (hasher.verify(pin, fresh)) {
            when {
                // A passkey saved with a different number of rounds is saved again with the current number.
                fresh.iterations != hasher.iterations -> store.save(hasher.create(pin))
                // Otherwise only write when something changed, so a right passkey doesn't rewrite the file.
                record.failures != 0 || record.lockStartedAt != null -> store.save(fresh.copy(failures = 0, lockStartedAt = null))
            }
            return PinCheck.Ok
        }
        val failures = fresh.failures + 1
        return if (failures >= MAX_FAILURES) {
            store.save(fresh.copy(failures = failures, lockStartedAt = elapsedMillis()))
            PinCheck.LockedOut(LOCKOUT_SECONDS)
        } else {
            store.save(fresh.copy(failures = failures))
            PinCheck.Wrong(MAX_FAILURES - failures)
        }
    }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCKOUT_SECONDS = 60
    }
}
