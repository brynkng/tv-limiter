package dev.tvlimiter.ui

import android.app.Activity
import android.content.Context
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

internal fun Context.label(text: CharSequence, sizeSp: Float = 18f, colorId: Int = R.color.text, bold: Boolean = false) =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color(colorId))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

internal fun Context.rounded(colorId: Int, radiusDp: Int = 8, strokeColorId: Int? = null) = GradientDrawable().apply {
    cornerRadius = dp(radiusDp).toFloat()
    setColor(color(colorId))
    strokeColorId?.let { setStroke(dp(3), color(it)) }
}

/** A remote-friendly button whose focus ring is easy to see from the couch. */
internal fun Context.tvButton(text: CharSequence, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    setTextColor(color(R.color.text))
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.panel, strokeColorId = R.color.accent))
        addState(intArrayOf(), rounded(R.color.panel))
    }
    setPadding(dp(20), dp(10), dp(20), dp(10))
    isFocusable = true
    setOnClickListener { onClick() }
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

/** Sets a padded, scrolling column as the screen's content and returns the column to fill. */
internal fun Activity.scrollScreen(): LinearLayout {
    val content = column().apply { setPadding(dp(48), dp(32), dp(48), dp(32)) }
    setContentView(
        ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg))
            addView(content)
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
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
