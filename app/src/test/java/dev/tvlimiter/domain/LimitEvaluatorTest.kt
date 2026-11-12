package dev.tvlimiter.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitEvaluatorTest {
    private val youtube = "com.google.android.youtube.tv"
    private val other = "com.example.other"
    private val tuesday = DayOfWeek.TUESDAY

    private val appLimit = Limit.newAppLimit(youtube).copy(minutesByDay = List(7) { 60 })
    private val tvLimit = Limit.defaultWholeTv().copy(minutesByDay = List(7) { 180 }, enabled = true)
    private val limits = listOf(tvLimit, appLimit)

    private fun usage(youtubeMin: Long, otherMin: Long) = DayUsage(
        date = "2026-11-10",
        secondsByPackage = mapOf(youtube to youtubeMin * 60, other to otherMin * 60),
        tvSeconds = (youtubeMin + otherMin) * 60,
    )

    @Test
    fun `app limit blocks the app and the TV limit blocks everything else when both run out`() {
        val before = LimitEvaluator.evaluate(limits, usage(50, 120), tuesday, youtube)
        assertNull(before.blocking)

        val after = usage(60, 120)
        val onYoutube = LimitEvaluator.evaluate(limits, after, tuesday, youtube)
        assertEquals(appLimit.id, onYoutube.blocking?.limit?.id)

        val onOther = LimitEvaluator.evaluate(limits, after, tuesday, other)
        assertEquals(tvLimit.id, onOther.blocking?.limit?.id)
    }

    @Test
    fun `the limit with less time left is reported and ties go to the app limit`() {
        val overApp = usage(70, 100) // app -10 min, tv 0
        assertEquals(appLimit.id, LimitEvaluator.evaluate(limits, overApp, tuesday, youtube).blocking?.limit?.id)

        val overTv = usage(60, 130) // app 0, tv -10 min
        assertEquals(tvLimit.id, LimitEvaluator.evaluate(limits, overTv, tuesday, youtube).blocking?.limit?.id)

        val tie = LimitEvaluator.evaluate(limits, usage(60, 120), tuesday, youtube)
        assertEquals(listOf(appLimit.id, tvLimit.id), tie.exhausted.map { it.limit.id })
    }

    @Test
    fun `a grant adds time to that limit only`() {
        val granted = UsageLedger.grant(usage(60, 100), appLimit.id, GrantChoice.PLUS_30, java.time.LocalDate.parse("2026-11-10"))
        val eval = LimitEvaluator.evaluate(limits, granted, tuesday, youtube)
        val app = eval.covering.first { it.limit.id == appLimit.id }
        val tv = eval.covering.first { it.limit.id == tvLimit.id }
        assertEquals(30 * 60L, app.remainingSeconds)
        assertEquals(20 * 60L, tv.remainingSeconds)
        assertNull(eval.blocking)
    }

    @Test
    fun `rest of day grant means the limit never blocks today`() {
        val granted = usage(500, 0).copy(restOfDayLimitIds = setOf(appLimit.id))
        val eval = LimitEvaluator.evaluate(listOf(appLimit), granted, tuesday, youtube)
        assertNull(eval.blocking)
        assertNull(eval.covering.single().remainingSeconds)
    }

    @Test
    fun `a no limit day never blocks`() {
        val saturdayFree = appLimit.copy(minutesByDay = List(7) { if (it == 5) null else 0 })
        assertNull(LimitEvaluator.evaluate(listOf(saturdayFree), usage(900, 0), DayOfWeek.SATURDAY, youtube).blocking)
        assertTrue(LimitEvaluator.evaluate(listOf(saturdayFree), usage(0, 0), DayOfWeek.SUNDAY, youtube).blocking != null)
    }

    @Test
    fun `a disabled limit never blocks`() {
        val off = appLimit.copy(enabled = false)
        assertNull(LimitEvaluator.evaluate(listOf(off), usage(900, 0), tuesday, youtube).blocking)
    }

    @Test
    fun `an app with no app limit is governed by the TV limit alone`() {
        val eval = LimitEvaluator.evaluate(limits, usage(0, 180), tuesday, other)
        assertEquals(listOf(tvLimit.id), eval.covering.map { it.limit.id })
        assertEquals(tvLimit.id, eval.blocking?.limit?.id)
    }

    @Test
    fun `a package with no covering limits is never blocked`() {
        val eval = LimitEvaluator.evaluate(listOf(appLimit), usage(0, 999), tuesday, other)
        assertTrue(eval.covering.isEmpty())
        assertNull(eval.blocking)
    }
}
