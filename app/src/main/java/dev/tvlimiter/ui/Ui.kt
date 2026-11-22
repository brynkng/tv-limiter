package dev.tvlimiter.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.LimitScope

/** Small helpers for building the D-pad screens in code. */
internal fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

internal fun Context.color(id: Int): Int = getColor(id)

internal enum class Weight(val fontId: Int) { REGULAR(R.font.lexend_regular), MEDIUM(R.font.lexend_medium), SEMIBOLD(R.font.lexend_semibold) }

internal fun Context.font(weight: Weight): Typeface = resources.getFont(weight.fontId)

internal fun Context.label(
    text: CharSequence,
    sizeSp: Float = 18f,
    colorId: Int = R.color.text,
    bold: Boolean = false,
    weight: Weight = if (bold) Weight.SEMIBOLD else Weight.REGULAR,
) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color(colorId))
    typeface = font(weight)
}

internal fun Context.rounded(colorId: Int, radiusDp: Int = 12, strokeColorId: Int? = null) = GradientDrawable().apply {
    cornerRadius = dp(radiusDp).toFloat()
    setColor(color(colorId))
    strokeColorId?.let { setStroke(dp(3), color(it)) }
}

/** Grows a focused view a little, so the focused item stands out from across the room. */
internal fun View.growOnFocus() {
    setOnFocusChangeListener { view, focused ->
        val scale = if (focused) FOCUS_SCALE else 1f
        view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }
}

private const val FOCUS_SCALE = 1.04f

/** A remote-friendly pill button. Focused, it turns solid mint with dark text. */
internal fun Context.tvButton(text: CharSequence, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    stateListAnimator = null
    typeface = font(Weight.MEDIUM)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(color(R.color.on_accent), color(R.color.text)),
        ),
    )
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.accent, radiusDp = 28))
        addState(intArrayOf(), rounded(R.color.panel, radiusDp = 28))
    }
    minHeight = dp(52)
    minimumHeight = dp(52)
    setPadding(dp(28), dp(12), dp(28), dp(12))
    isFocusable = true
    growOnFocus()
    setOnClickListener { onClick() }
}

/** A focusable row that holds other views. Focused, it gets a mint outline and grows. */
internal fun Context.focusRow(onClick: () -> Unit) = row().apply {
    isFocusable = true
    isClickable = true
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.panel_raised, radiusDp = 16, strokeColorId = R.color.accent))
        addState(intArrayOf(), rounded(R.color.panel, radiusDp = 16))
    }
    setPadding(dp(24), dp(16), dp(24), dp(16))
    growOnFocus()
    setOnClickListener { onClick() }
}

/**
 * A horizontal bar for an amount of time. The mint part is what is left; when nothing is left
 * the whole track turns pink. A null fraction draws an empty track, for "no limit".
 */
internal class TimeBar(context: Context) : View(context) {
    var fraction: Float? = null
        set(value) {
            field = value
            invalidate()
        }
    var exhausted = false
        set(value) {
            field = value
            invalidate()
        }
    /** The fill color resource; mint unless a caller picks another. */
    var fillColorId = R.color.accent
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = context.color(if (exhausted) R.color.warn else R.color.track)
        canvas.drawRoundRect(rect, radius, radius, paint)
        val filled = fraction?.coerceIn(0f, 1f) ?: return
        if (exhausted || filled <= 0f) return
        rect.right = maxOf(height.toFloat(), width * filled)
        paint.color = context.color(fillColorId)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}

internal fun Context.column(gravity: Int = Gravity.START) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    this.gravity = gravity
}

internal fun Context.row() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

internal fun View.margins(context: Context, all: Int = 0, top: Int = all, end: Int = all): View {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = context.dp(top)
        marginEnd = context.dp(end)
    }
    return this
}

/** Stretches a view across its row, with an optional top margin. */
internal fun View.fullWidth(context: Context, top: Int = 0): View {
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = context.dp(top) }
    return this
}

/** Sets a padded, scrolling column as the screen's content and returns the column to fill. */
internal fun Activity.scrollScreen(): LinearLayout {
    val content = column().apply {
        setPadding(dp(64), dp(40), dp(64), dp(40))
        clipChildren = false
        clipToPadding = false
    }
    setContentView(
        ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg))
            isFillViewport = true
            clipChildren = false
            addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        },
    )
    return content
}

internal fun Context.limitName(limit: Limit): String = when (val scope = limit.scope) {
    LimitScope.WholeTv -> "TV time"
    is LimitScope.App -> BlockActivity.appLabel(packageManager, scope.packageName)
}

internal fun formatMinutes(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
