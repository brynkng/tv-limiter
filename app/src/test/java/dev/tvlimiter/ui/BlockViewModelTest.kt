package dev.tvlimiter.ui

import dev.tvlimiter.domain.DayUsage
import dev.tvlimiter.domain.GrantChoice
import dev.tvlimiter.domain.InMemoryPinStore
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.domain.PinGuard
import dev.tvlimiter.domain.PinHasher
import dev.tvlimiter.domain.UsageLedger
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockViewModelTest {
    private val youtube = "com.google.android.youtube.tv"
    private val day = LocalDate.parse("2026-11-10")
    private val appLimit = Limit.newAppLimit(youtube)
    private val tvLimit = Limit.defaultWholeTv().copy(enabled = true)
    private val guard = PinGuard(InMemoryPinStore(), PinHasher(iterations = 1_000)) { 0L }.apply { setPin("2468") }
    private var usage = DayUsage(date = day.toString())
    private val grants = mutableListOf<Pair<String, GrantChoice>>()
    private var parentModeStarted = false

    private fun model(limits: List<Limit> = listOf(tvLimit, appLimit)) = BlockViewModel(
        blockedPackage = youtube,
        limits = { limits },
        usage = { usage },
        weekday = { DayOfWeek.TUESDAY },
        recordGrant = { id, choice ->
            grants += id to choice
            usage = UsageLedger.grant(usage, id, choice, day)
        },
        guard = guard,
        appLabel = { "YouTube" },
        startParentMode = { parentModeStarted = true },
    )

    private fun use(minutesOnYoutube: Long, otherMinutes: Long = 0) {
        usage = UsageLedger.charge(usage, youtube, minutesOnYoutube * 60, day)
        usage = UsageLedger.charge(usage, "other", otherMinutes * 60, day)
    }

    @Test
    fun `the headline names the app or TV time`() {
        use(60)
        assertEquals("YouTube is done for today", model().headline)
        usage = DayUsage(date = day.toString())
        use(10, 170)
        assertEquals("TV time is done for today", model().headline)
    }

    @Test
    fun `a correct PIN opens the grant menu and a wrong one stays on the pad`() {
        use(60)
        val model = model()
        model.requestPin()
        assertEquals(PinCheck.Wrong(4), model.submitPin("1111"))
        assertEquals(BlockStep.PIN, model.step)
        assertEquals(PinCheck.Ok, model.submitPin("2468"))
        assertEquals(BlockStep.GRANT, model.step)
    }

    @Test
    fun `plus 15 is recorded for the fired limit only and lets the app back in`() {
        use(60)
        val model = model()
        model.requestPin()
        model.submitPin("2468")
        model.grant(GrantChoice.PLUS_15)
        assertEquals(listOf(appLimit.id to GrantChoice.PLUS_15), grants)
        assertEquals(BlockStep.DONE, model.step)
    }

    @Test
    fun `rest of day records an unlimited grant for that limit`() {
        use(60)
        val model = model()
        model.requestPin()
        model.submitPin("2468")
        model.grant(GrantChoice.REST_OF_DAY)
        assertTrue(appLimit.id in usage.restOfDayLimitIds)
        assertEquals(BlockStep.DONE, model.step)
    }

    @Test
    fun `when both limits ran out together the second grant needs no second PIN`() {
        use(60, 120)
        val model = model()
        assertEquals("YouTube is done for today", model.headline)
        model.requestPin()
        model.submitPin("2468")
        model.grant(GrantChoice.PLUS_30)
        assertEquals(BlockStep.GRANT, model.step)
        assertEquals("TV time is done for today", model.headline)
        model.grant(GrantChoice.PLUS_30)
        assertEquals(listOf(appLimit.id to GrantChoice.PLUS_30, tvLimit.id to GrantChoice.PLUS_30), grants)
        assertEquals(BlockStep.DONE, model.step)
    }

    @Test
    fun `while locked out the pad refuses entry with a countdown message`() {
        use(60)
        val model = model()
        model.requestPin()
        repeat(5) { model.submitPin("0000") }
        val check = model.submitPin("2468")
        assertTrue(check is PinCheck.LockedOut)
        assertEquals("Try again in 60s", pinFeedback(check))
        assertEquals("Wrong passkey, 1 try left", pinFeedback(PinCheck.Wrong(1)))
        assertEquals(BlockStep.PIN, model.step)
    }

    @Test
    fun `grants are ignored until the PIN is entered`() {
        use(60)
        val model = model()
        model.grant(GrantChoice.REST_OF_DAY)
        assertTrue(grants.isEmpty())
        assertEquals(BlockStep.CHOOSE, model.step)
    }

    @Test
    fun `nothing ran out means there is nothing to block`() {
        use(10)
        assertEquals(BlockStep.DONE, model().step)
    }

    @Test
    fun `parent mode needs the passkey, then lets the app resume without a grant`() {
        use(minutesOnYoutube = 60)
        val m = model()
        m.parentMode()
        assertEquals(false, parentModeStarted)

        m.requestPin()
        m.submitPin("2468")
        m.parentMode()

        assertTrue(parentModeStarted)
        assertEquals(BlockStep.DONE, m.step)
        assertEquals(emptyList<Pair<String, GrantChoice>>(), grants)
    }
}
