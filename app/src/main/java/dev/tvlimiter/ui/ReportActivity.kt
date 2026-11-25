package dev.tvlimiter.ui

import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import dev.tvlimiter.R
import dev.tvlimiter.data.HistoryRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.ReportPeriod
import dev.tvlimiter.domain.ReportRange
import dev.tvlimiter.domain.UsageReport
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Screen time over the last days, weeks or months: a chart of TV time with one bar per
 * period, and the apps used most in the selected period. Moving onto a tab switches the range;
 * left and right on the chart pick a period.
 */
class ReportActivity : GuardedActivity() {
    private lateinit var content: LinearLayout
    private val tabs = mutableMapOf<ReportRange, Button>()
    private lateinit var chart: UsageChart
    private lateinit var periodTitle: android.widget.TextView
    private lateinit var periodTotal: android.widget.TextView
    private lateinit var periodNote: android.widget.TextView
    private lateinit var apps: LinearLayout
    private lateinit var historyNote: android.widget.TextView
    private var range = ReportRange.DAYS
    private var periods: List<ReportPeriod> = emptyList()
    private val appNames = mutableMapOf<String, String>()
    /** The apps that get their own color on the chart this time, most used first. */
    private var colored: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = scrollScreen()
        build()
    }

    override fun onUnlockedResume() = load()

    private fun build() {
        val header = row()
        header.addView(label("Screen time", sizeSp = 34f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        ReportRange.entries.forEach { r ->
            val tab = tab(r.name.lowercase().replaceFirstChar { it.uppercase() }) { select(r) }
            tabs[r] = tab
            header.addView(tab.margins(this, end = if (r == ReportRange.entries.last()) 0 else 10))
        }
        content.addView(header.fullWidth(this))

        val body = row().apply {
            gravity = Gravity.TOP
            clipChildren = false
        }
        val left = column()
        periodTitle = label("", sizeSp = 18f, colorId = R.color.muted)
        periodTotal = label("", sizeSp = 40f, bold = true)
        periodNote = label("", sizeSp = 16f, colorId = R.color.muted)
        left.addView(periodTitle)
        left.addView(periodTotal)
        left.addView(periodNote)
        chart = UsageChart(this).apply {
            id = android.view.View.generateViewId()
            onSelect = { showPeriod(it) }
        }
        // The tabs sit up and to the right of the chart, so Up and Down are linked by hand.
        tabs.values.forEach { it.nextFocusDownId = chart.id }
        left.addView(chart, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(16) })
        body.addView(left, LinearLayout.LayoutParams(0, -2, 3f).apply { marginEnd = dp(32) })

        val right = column()
        right.addView(label("Most used", sizeSp = 20f, weight = Weight.MEDIUM))
        apps = column()
        right.addView(apps.fullWidth(this, top = 8))
        body.addView(right, LinearLayout.LayoutParams(0, -2, 2f))
        content.addView(body.fullWidth(this, top = 24))

        historyNote = label("", sizeSp = 14f, colorId = R.color.muted)
        content.addView(historyNote.margins(this, top = 20))
    }

    private fun load() {
        render()
        chart.requestFocus()
    }

    private fun select(next: ReportRange) {
        if (next == range && periods.isNotEmpty()) return
        range = next
        render()
    }

    private fun render() {
        val usageRepo = UsageRepository.get(this, profileId)
        val history = HistoryRepository.get(this, profileId).all()
        val today = LocalDate.now()
        periods = UsageReport.periods(history, usageRepo.today(), today, range)
        tabs.forEach { (r, tab) -> tab.isSelected = r == range }
        chart.nextFocusUpId = tabs.getValue(range).id
        colored = UsageReport.topPackages(periods, CHART_COLORS.size)
        chart.show(
            periods.map { UsageReport.segments(it, colored) },
            CHART_COLORS.take(colored.size) + R.color.chart_other,
            periods.map { it.label },
            periods.lastIndex,
        )
        showPeriod(periods.lastIndex)

        val first = history.days.firstOrNull()?.date?.let(LocalDate::parse) ?: today
        val firstShown = periods.firstOrNull()?.start
        historyNote.text = if (firstShown != null && first.isAfter(firstShown)) {
            "History starts ${first.format(DateTimeFormatter.ofPattern("MMMM d"))}, when TV Limiter began keeping it. Earlier days show as empty."
        } else {
            ""
        }
    }

    private fun showPeriod(index: Int) {
        val period = periods.getOrNull(index) ?: return
        periodTitle.text = period.title
        periodTotal.text = if (period.daysWithData == 0) "No data" else formatMinutes(period.tvSeconds)
        periodNote.text = when {
            period.daysWithData == 0 -> "Nothing was recorded for this period."
            range == ReportRange.DAYS -> "of TV time"
            else -> "of TV time, about ${formatMinutes(period.tvSeconds / period.daysWithData)} a day"
        }
        apps.removeAllViews()
        val top = period.topApps.take(MAX_APPS)
        if (top.isEmpty()) {
            apps.addView(label("No app time recorded.", sizeSp = 16f, colorId = R.color.muted).margins(this, top = 8))
            return
        }
        val most = top.first().second.toFloat()
        top.forEach { (pkg, seconds) ->
            val name = appNames.getOrPut(pkg) { BlockActivity.appLabel(packageManager, pkg) }
            val item = column()
            val colorId = colorFor(pkg)
            val line = row().apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(
                android.view.View(this@ReportActivity).apply { background = rounded(colorId, radiusDp = 6) },
                LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) },
            )
            line.addView(label(name, sizeSp = 17f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label(formatMinutes(seconds), sizeSp = 17f, weight = Weight.MEDIUM))
            item.addView(line.fullWidth(this))
            val bar = TimeBar(this).apply {
                fraction = seconds / most
                fillColorId = colorId
            }
            item.addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(6) })
            apps.addView(item.fullWidth(this, top = 14))
        }
    }

    private fun colorFor(pkg: String): Int = CHART_COLORS.getOrElse(colored.indexOf(pkg)) { R.color.chart_other }

    /** A range tab. It switches as soon as the remote moves onto it, like TV menus do. */
    private fun tab(text: String, onPick: () -> Unit) = Button(this).apply {
        id = android.view.View.generateViewId()
        this.text = text
        isAllCaps = false
        stateListAnimator = null
        typeface = font(Weight.MEDIUM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(color(R.color.on_accent), color(R.color.accent), color(R.color.muted)),
            ),
        )
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.accent, radiusDp = 24))
            addState(intArrayOf(android.R.attr.state_selected), rounded(R.color.panel_raised, radiusDp = 24))
            addState(intArrayOf(), rounded(R.color.bg, radiusDp = 24))
        }
        minHeight = dp(44)
        minimumHeight = dp(44)
        setPadding(dp(22), dp(8), dp(22), dp(8))
        isFocusable = true
        setOnFocusChangeListener { _, focused -> if (focused) onPick() }
        setOnClickListener { onPick() }
    }

    private companion object {
        const val MAX_APPS = 6
        val CHART_COLORS = listOf(R.color.chart_1, R.color.chart_2, R.color.chart_3, R.color.chart_4)
    }
}
