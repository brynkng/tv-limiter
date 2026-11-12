package dev.tvlimiter.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.LimitScope
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Edits one limit's seven day values with left and right on the remote. Every change is saved at once. */
class ScheduleEditorActivity : GuardedActivity() {
    private lateinit var content: LinearLayout
    private lateinit var limitId: String
    private lateinit var state: ScheduleEditorState
    private val dayRows = mutableListOf<Button>()
    private var enableButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        limitId = intent.getStringExtra(EXTRA_LIMIT_ID) ?: return finish()
        content = column().apply { setPadding(dp(48), dp(32), dp(48), dp(32)) }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(color(R.color.bg))
                addView(content)
            },
        )
    }

    override fun onUnlockedResume() {
        val limit = LimitsRepository.get(this).find(limitId) ?: return finish()
        state = ScheduleEditorState(limit.minutesByDay)
        build(limit)
    }

    private fun build(limit: Limit) {
        content.removeAllViews()
        dayRows.clear()
        val name = when (val scope = limit.scope) {
            LimitScope.WholeTv -> "TV time"
            is LimitScope.App -> BlockActivity.appLabel(packageManager, scope.packageName)
        }
        content.addView(label(name, sizeSp = 30f, bold = true))
        content.addView(
            label(
                "Left and right change a day by 15 minutes. Going below 0 means no limit that day.",
                sizeSp = 15f,
                colorId = R.color.muted,
            ).margins(this, top = 8),
        )
        DayOfWeek.entries.forEachIndexed { index, day ->
            val row = tvButton("") {}
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
            row.minWidth = dp(420)
            row.tag = day
            dayRows += row
            content.addView(row.margins(this, top = 8))
        }

        val actions = row()
        actions.addView(tvButton("Copy Monday to weekdays") { state.copyMondayToWeekdays(); save() }.margins(this, end = 12))
        actions.addView(tvButton("Copy Saturday to weekend") { state.copySaturdayToWeekend(); save() }.margins(this, end = 12))
        enableButton = tvButton("") { toggleEnabled() }.also { actions.addView(it.margins(this, end = 12)) }
        if (!limit.isWholeTv) {
            actions.addView(
                tvButton("Delete") {
                    LimitsRepository.get(this).delete(limitId)
                    finish()
                },
            )
        }
        content.addView(actions.margins(this, top = 24))
        refresh(limit)
        dayRows.first().requestFocus()
    }

    private fun save() {
        val repo = LimitsRepository.get(this)
        val limit = repo.find(limitId) ?: return finish()
        val updated = limit.copy(minutesByDay = state.minutes)
        repo.upsert(updated)
        refresh(updated)
    }

    private fun toggleEnabled() {
        val repo = LimitsRepository.get(this)
        val limit = repo.find(limitId) ?: return finish()
        val updated = limit.copy(enabled = !limit.enabled)
        repo.upsert(updated)
        refresh(updated)
    }

    private fun refresh(limit: Limit) {
        dayRows.forEachIndexed { index, row ->
            val day = (row.tag as DayOfWeek).getDisplayName(TextStyle.FULL, Locale.getDefault())
            row.text = "◀  $day: ${ScheduleEditorState.describe(state.minutes[index])}  ▶"
        }
        enableButton?.text = if (limit.enabled) "Turn off" else "Turn on"
    }

    companion object {
        private const val EXTRA_LIMIT_ID = "limit_id"

        fun intent(context: Context, limitId: String): Intent =
            Intent(context, ScheduleEditorActivity::class.java).putExtra(EXTRA_LIMIT_ID, limitId)
    }
}
