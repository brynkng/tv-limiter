package dev.tvlimiter.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Limit
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Edits one limit: today's standing, any time the owner added today, and the seven day values,
 * changed with left and right on the remote. Every change is saved at once.
 */
class ScheduleEditorActivity : GuardedActivity() {
    private lateinit var content: LinearLayout
    private lateinit var limitId: String
    private lateinit var state: ScheduleEditorState
    private val dayBars = mutableListOf<TimeBar>()
    private val dayValues = mutableListOf<TextView>()
    private var enableButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        limitId = intent.getStringExtra(EXTRA_LIMIT_ID) ?: return finish()
        content = scrollScreen()
    }

    override fun onUnlockedResume() {
        val limit = LimitsRepository.get(this).find(limitId) ?: return finish()
        state = ScheduleEditorState(limit.minutesByDay)
        build(limit)
    }

    private fun build(limit: Limit) {
        content.removeAllViews()
        dayBars.clear()
        dayValues.clear()
        val usageRepo = UsageRepository.get(this)
        val today = usageRepo.weekday()
        val summary = LimitSummary.of(limit, usageRepo.today(), today)

        content.addView(label(limitName(limit), sizeSp = 34f, bold = true))
        content.addView(
            label("Today: ${summary.status}", sizeSp = 20f, colorId = if (summary.exhausted) R.color.warn else R.color.muted)
                .margins(this, top = 6),
        )
        summary.added?.let { addedTimeRow(it) }

        content.addView(
            label("How long your child can use this each day. Left and right change a day by 15 minutes; below 0 means no limit.", sizeSp = 15f, colorId = R.color.muted)
                .margins(this, top = 20),
        )
        var firstDay: LinearLayout? = null
        DayOfWeek.entries.forEachIndexed { index, day ->
            val row = dayRow(index, day, isToday = day == today)
            content.addView(row.fullWidth(this, top = if (index == 0) 12 else 6))
            if (firstDay == null) firstDay = row
        }

        val copies = row().apply { clipChildren = false }
        copies.addView(tvButton("Copy Monday to every day") { state.copyMondayToAllDays(); save() }.margins(this, end = 12))
        copies.addView(tvButton("Copy Monday to weekdays") { state.copyMondayToWeekdays(); save() }.margins(this, end = 12))
        copies.addView(tvButton("Copy Saturday to Sunday") { state.copySaturdayToWeekend(); save() })
        content.addView(copies.margins(this, top = 28))

        val actions = row().apply { clipChildren = false }
        enableButton = tvButton("") { toggleEnabled() }.also { actions.addView(it.margins(this, end = 12)) }
        if (!limit.isWholeTv) {
            actions.addView(
                tvButton("Delete this limit") {
                    LimitsRepository.get(this).delete(limitId)
                    finish()
                },
            )
        }
        content.addView(actions.margins(this, top = 12))
        refresh(limit)
        firstDay?.requestFocus()
    }

    /** "Added today" with a button that takes the added time back. */
    private fun addedTimeRow(added: String) {
        val row = row().apply {
            background = rounded(R.color.bg, radiusDp = 16, strokeColorId = R.color.panel_raised)
            clipChildren = false
            setPadding(dp(24), dp(12), dp(12), dp(12))
        }
        row.addView(label("You added $added today.", sizeSp = 18f), LinearLayout.LayoutParams(0, -2, 1f))
        val button = tvButton("Remove added time") {
            UsageRepository.get(this).clearGrants(limitId)
            LimitsRepository.get(this).find(limitId)?.let { build(it) }
        }
        row.addView(button)
        content.addView(row.fullWidth(this, top = 20))
    }

    private fun dayRow(index: Int, day: DayOfWeek, isToday: Boolean): LinearLayout {
        val row = focusRow {}.apply { setPadding(dp(24), dp(10), dp(24), dp(10)) }
        val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault()) + if (isToday) " (today)" else ""
        row.addView(label(name, sizeSp = 20f, weight = if (isToday) Weight.SEMIBOLD else Weight.REGULAR), LinearLayout.LayoutParams(dp(220), -2))
        val bar = TimeBar(this)
        row.addView(bar, LinearLayout.LayoutParams(0, dp(10), 1f).apply { marginEnd = dp(28) })
        val value = label("", sizeSp = 20f, weight = Weight.MEDIUM).apply { gravity = Gravity.END }
        row.addView(value, LinearLayout.LayoutParams(dp(280), -2))
        dayBars += bar
        dayValues += value
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> state.left(index)
                KeyEvent.KEYCODE_DPAD_RIGHT -> state.right(index)
                else -> return@setOnKeyListener false
            }
            save()
            true
        }
        return row
    }

    private fun save() = update { it.copy(minutesByDay = state.minutes) }

    private fun toggleEnabled() = update { it.copy(enabled = !it.enabled) }

    private fun update(change: (Limit) -> Limit) {
        val repo = LimitsRepository.get(this)
        val limit = repo.find(limitId) ?: return finish()
        val updated = change(limit)
        repo.upsert(updated)
        refresh(updated)
    }

    private fun refresh(limit: Limit) {
        // Bars share one scale so days compare at a glance; at least 4 hours keeps short days readable.
        val scale = maxOf(BAR_SCALE_MINUTES, state.minutes.maxOf { it ?: 0 }).toFloat()
        state.minutes.forEachIndexed { index, minutes ->
            dayValues[index].text = "‹  ${ScheduleEditorState.describe(minutes)}  ›"
            dayValues[index].setTextColor(color(if (minutes == null) R.color.muted else R.color.text))
            dayBars[index].fraction = minutes?.let { it / scale }
        }
        enableButton?.text = if (limit.enabled) "Turn this limit off" else "Turn this limit on"
    }

    companion object {
        private const val EXTRA_LIMIT_ID = "limit_id"
        private const val BAR_SCALE_MINUTES = 4 * 60

        fun intent(context: Context, limitId: String): Intent =
            Intent(context, ScheduleEditorActivity::class.java).putExtra(EXTRA_LIMIT_ID, limitId)
    }
}
