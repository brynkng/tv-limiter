package dev.tvlimiter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.View
import dev.tvlimiter.R

/**
 * A stacked bar chart of screen time, one bar per period, split into one colored segment per
 * app. The scale is whole hours (or 15-minute steps when every bar is short). Left and right on
 * the remote move the selected bar, which stays bright with its total above it while the
 * others dim.
 */
internal class UsageChart(context: Context) : View(context) {
    private var stacks: List<List<Long>> = emptyList()
    private var values: List<Long> = emptyList()
    private var colorIds: List<Int> = emptyList()
    private var labels: List<String> = emptyList()
    var selected = 0
        private set
    var onSelect: (Int) -> Unit = {}

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = context.dp(1).toFloat() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.font(Weight.REGULAR) }
    private val strong = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.font(Weight.SEMIBOLD) }
    private val rect = RectF()
    private val clip = Path()

    init {
        isFocusable = true
        defaultFocusHighlightEnabled = false
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), context.rounded(R.color.panel, radiusDp = 16, strokeColorId = R.color.accent))
            addState(intArrayOf(), context.rounded(R.color.panel, radiusDp = 16))
        }
        val pad = context.dp(20)
        setPadding(pad, pad, pad, pad)
    }

    /** [stacks] holds each bar's segments, bottom first, colored by [colorIds] in the same order. */
    fun show(stacks: List<List<Long>>, colorIds: List<Int>, labels: List<String>, selected: Int) {
        this.stacks = stacks
        this.colorIds = colorIds
        this.values = stacks.map { it.sum() }
        this.labels = labels
        this.selected = selected.coerceIn(0, (values.size - 1).coerceAtLeast(0))
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val next = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> selected - 1
            KeyEvent.KEYCODE_DPAD_RIGHT -> selected + 1
            else -> return super.onKeyDown(keyCode, event)
        }
        // At either end the key is kept, so focus doesn't jump off the chart by accident.
        if (next in values.indices) {
            selected = next
            invalidate()
            onSelect(next)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val sp = resources.displayMetrics.scaledDensity
        text.textSize = 13 * sp
        strong.textSize = 15 * sp
        val axisWidth = context.dp(52).toFloat()
        val left = paddingLeft + axisWidth
        val right = (width - paddingRight).toFloat()
        val top = paddingTop + strong.textSize * 1.6f
        val bottom = height - paddingBottom - text.textSize * 1.8f

        // Scale: the smallest step that keeps about four gridlines above the tallest bar.
        val maxMinutes = (values.maxOrNull() ?: 0L) / 60f
        val step = STEPS_MINUTES.firstOrNull { it * 4 >= maxMinutes } ?: STEPS_MINUTES.last()
        val lines = maxOf(1, kotlin.math.ceil(maxMinutes / step).toInt())
        val scaleMinutes = (step * lines).toFloat()
        val plotHeight = bottom - top

        grid.color = context.color(R.color.track)
        text.color = context.color(R.color.muted)
        text.textAlign = Paint.Align.RIGHT
        for (i in 0..lines) {
            val y = bottom - plotHeight * i / lines
            canvas.drawLine(left, y, right, y, grid)
            canvas.drawText(axisLabel(step * i), left - context.dp(10), y + text.textSize / 3, text)
        }

        val slot = (right - left) / values.size
        val barWidth = minOf(slot * 0.56f, context.dp(56).toFloat())
        val radius = context.dp(6).toFloat()
        values.forEachIndexed { i, seconds ->
            val cx = left + slot * (i + 0.5f)
            // Short bars are drawn at least two corners tall, so the label sits above what's drawn.
            val h = if (seconds > 0) maxOf(plotHeight * (seconds / 60f) / scaleMinutes, radius * 2) else 0f
            val isSelected = i == selected
            if (isSelected) {
                // A soft column behind the selected bar, so it reads as picked even when short.
                rect.set(cx - slot / 2 + context.dp(4), top - strong.textSize * 0.4f, cx + slot / 2 - context.dp(4), bottom)
                bar.color = context.color(R.color.panel_raised)
                bar.alpha = 255
                canvas.drawRoundRect(rect, radius, radius, bar)
            }
            if (h > 0) {
                rect.set(cx - barWidth / 2, bottom - h, cx + barWidth / 2, bottom)
                clip.reset()
                clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                var y = bottom
                stacks[i].forEachIndexed { s, part ->
                    if (part <= 0) return@forEachIndexed
                    val partHeight = h * part / seconds.toFloat()
                    bar.color = context.color(colorIds.getOrElse(s) { R.color.chart_other })
                    bar.alpha = if (isSelected) 255 else DIMMED
                    canvas.drawRect(rect.left, y - partHeight, rect.right, y, bar)
                    y -= partHeight
                }
                canvas.restore()
            }
            text.textAlign = Paint.Align.CENTER
            text.color = context.color(if (isSelected) R.color.text else R.color.muted)
            canvas.drawText(labels.getOrElse(i) { "" }, cx, height - paddingBottom - text.textSize * 0.3f, text)
            if (isSelected) {
                strong.textAlign = Paint.Align.CENTER
                strong.color = context.color(R.color.text)
                val labelY = (bottom - h - context.dp(8)).coerceAtLeast(paddingTop + strong.textSize)
                canvas.drawText(formatMinutes(seconds), cx, labelY, strong)
            }
        }
    }

    private fun axisLabel(minutes: Int): String = when {
        minutes == 0 -> "0"
        minutes % 60 == 0 -> "${minutes / 60} h"
        minutes > 60 -> "${minutes / 60} h ${minutes % 60}"
        else -> "$minutes m"
    }

    private companion object {
        const val DIMMED = 110
        val STEPS_MINUTES = listOf(15, 30, 60, 120, 180, 240, 360, 480, 720, 1440, 2880, 7200)
    }
}
