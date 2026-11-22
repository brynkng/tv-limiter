package dev.tvlimiter.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvlimiter.R

/**
 * A placeholder shaped like the screen that opens after a right passkey, shown while it loads,
 * so the passkey circles never linger. Each shape matches the real screen's padding and sizes.
 */
enum class Skeleton {
    /** The home list and the edit screen: a title, then full-width rows. */
    LIST,

    /** The app picker: a title, then a grid of tiles. */
    GRID,
    ;

    fun into(intent: Intent): Intent = intent.putExtra(EXTRA, name)

    companion object {
        private const val EXTRA = "skeleton"

        fun from(intent: Intent): Skeleton =
            intent.getStringExtra(EXTRA)?.let { name -> entries.firstOrNull { it.name == name } } ?: LIST
    }
}

/** Swaps the screen for [skeleton], then closes this screen with no slide, so the real one appears in its place. */
internal fun Activity.finishIntoSkeleton(skeleton: Skeleton) {
    setContentView(skeletonView(skeleton))
    window.decorView.post {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

private fun Activity.skeletonView(skeleton: Skeleton): View {
    val content = column().apply { setPadding(dp(64), dp(40), dp(64), dp(40)) }
    content.addView(block(widthDp = 300, heightDp = 40))
    content.addView(block(widthDp = 260, heightDp = 18, topDp = 12))
    when (skeleton) {
        Skeleton.LIST -> repeat(3) { index ->
            val row = row().apply {
                background = rounded(R.color.panel, radiusDp = 16)
                setPadding(dp(24), dp(22), dp(24), dp(22))
            }
            row.addView(block(widthDp = 160, heightDp = 22, colorId = R.color.track))
            row.addView(View(this), LinearLayout.LayoutParams(dp(80), 1))
            row.addView(block(heightDp = 12, colorId = R.color.track), LinearLayout.LayoutParams(0, dp(12), 1f))
            row.addView(View(this), LinearLayout.LayoutParams(dp(80), 1))
            row.addView(block(widthDp = 120, heightDp = 18, colorId = R.color.track))
            content.addView(row.fullWidth(this, top = if (index == 0) 32 else 12))
        }
        Skeleton.GRID -> repeat(2) { index ->
            val line = row()
            repeat(4) {
                line.addView(
                    block(heightDp = 0, colorId = R.color.panel, radiusDp = 16),
                    LinearLayout.LayoutParams(dp(188), dp(150)).apply { marginEnd = dp(20) },
                )
            }
            content.addView(line.margins(this, top = if (index == 0) 28 else 20))
        }
    }
    ObjectAnimator.ofFloat(content, View.ALPHA, 1f, 0.55f).apply {
        duration = 600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        start()
    }
    return FrameLayout(this).apply {
        setBackgroundColor(color(R.color.bg))
        addView(content, FrameLayout.LayoutParams(-1, -2))
    }
}

private fun Activity.block(
    widthDp: Int = 0,
    heightDp: Int,
    topDp: Int = 0,
    colorId: Int = R.color.panel,
    radiusDp: Int = 8,
): View = View(this).apply {
    background = rounded(colorId, radiusDp = radiusDp)
    layoutParams = LinearLayout.LayoutParams(if (widthDp > 0) dp(widthDp) else -1, dp(heightDp)).apply { topMargin = dp(topDp) }
}
