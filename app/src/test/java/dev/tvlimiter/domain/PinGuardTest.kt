package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryPinStore : PinStore {
    var record: PinRecord? = null
    var damaged = false
    var saves = 0
    override fun load() = record
    override fun save(record: PinRecord) {
        this.record = record
        saves++
    }
    override fun isDamaged() = damaged
}

class PinGuardTest {
    private val store = InMemoryPinStore()
    private var now = 10_000L
    private val hasher = PinHasher(iterations = 1_000)
    private fun guard() = PinGuard(store, hasher) { now }

    @Test
    fun `wrong PINs count down and the fifth locks entry`() {
        val guard = guard().apply { setPin("1234") }
        assertEquals(PinCheck.Wrong(4), guard.check("0000"))
        repeat(3) { guard.check("0000") }
        assertEquals(PinCheck.LockedOut(60), guard.check("0000"))
    }

    @Test
    fun `while locked even the right PIN is refused, and after 60 s it works`() {
        val guard = guard().apply { setPin("1234") }
        repeat(5) { guard.check("0000") }
        now += 30_000
        assertEquals(PinCheck.LockedOut(30), guard.check("1234"))
        now += 30_000
        assertEquals(PinCheck.Ok, guard.check("1234"))
    }

    @Test
    fun `a success resets the failure count`() {
        val guard = guard().apply { setPin("1234") }
        repeat(4) { guard.check("0000") }
        assertEquals(PinCheck.Ok, guard.check("1234"))
        assertEquals(PinCheck.Wrong(4), guard.check("0000"))
    }

    @Test
    fun `the lockout survives building a new guard from the stored record`() {
        guard().apply { setPin("1234") }.also { g -> repeat(5) { g.check("0000") } }
        val fresh = guard()
        assertTrue(fresh.check("1234") is PinCheck.LockedOut)
    }

    @Test
    fun `a reboot during a lockout starts the minute again`() {
        val guard = guard().apply { setPin("1234") }
        repeat(5) { guard.check("0000") }
        now = 500 // the monotonic clock restarted
        assertEquals(60, guard.lockoutSecondsLeft())
        now += 60_000
        assertEquals(PinCheck.Ok, guard.check("1234"))
    }

    @Test
    fun `no PIN stored reports so`() {
        assertEquals(PinCheck.NoPin, guard().check("1234"))
    }

    @Test
    fun `a damaged PIN file is refused rather than treated as no PIN`() {
        store.damaged = true
        val guard = guard()
        assertTrue(guard.isDamaged())
        assertEquals(PinCheck.Damaged, guard.check("1234"))
    }

    @Test
    fun `a right PIN with no failures does not rewrite the file`() {
        val guard = guard().apply { setPin("1234") }
        val before = store.saves
        assertEquals(PinCheck.Ok, guard.check("1234"))
        assertEquals(before, store.saves)
    }

    @Test
    fun `a wrong PIN after an expired lockout starts a fresh count`() {
        val guard = guard().apply { setPin("1234") }
        repeat(5) { guard.check("0000") }
        now += 60_000
        assertEquals(PinCheck.Wrong(4), guard.check("0000"))
    }
}
