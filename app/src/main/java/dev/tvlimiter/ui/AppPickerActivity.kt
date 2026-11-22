package dev.tvlimiter.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Limit

/** A grid of the apps that can be opened from the TV home screen, most used today first. */
class AppPickerActivity : GuardedActivity() {
    private lateinit var content: LinearLayout
    override val skeleton = Skeleton.GRID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = scrollScreen()
    }

    override fun onUnlockedResume() {
        content.removeAllViews()
        content.addView(label("Which app should have a limit?", sizeSp = 34f, bold = true))
        val usage = UsageRepository.get(this).today()
        val limited = LimitsRepository.get(this).all().map { it.id }.toSet()
        val apps = launchablePackages(packageManager, packageName)
            .map { it to BlockActivity.appLabel(packageManager, it) }
            .sortedWith(compareByDescending<Pair<String, String>> { usage.secondsFor(it.first) }.thenBy { it.second.lowercase() })
        if (apps.isEmpty()) {
            content.addView(label("No apps found.", colorId = R.color.muted).margins(this, top = 16))
            return
        }
        var first: View? = null
        apps.chunked(COLUMNS).forEachIndexed { rowIndex, chunk ->
            val line = row().apply { clipChildren = false }
            chunk.forEach { (pkg, name) ->
                val used = usage.secondsFor(pkg)
                val note = when {
                    Limit.appLimitId(pkg) in limited -> "Has a limit"
                    used > 0 -> "${formatMinutes(used)} today"
                    else -> ""
                }
                val tile = appTile(pkg, name, note)
                line.addView(tile, LinearLayout.LayoutParams(dp(TILE_WIDTH), -2).apply { marginEnd = dp(20) })
                if (first == null) first = tile
            }
            content.addView(line.margins(this, top = if (rowIndex == 0) 28 else 20))
        }
        first?.requestFocus()
    }

    private fun appTile(pkg: String, name: String, note: String): View {
        val tile = column().apply {
            isFocusable = true
            isClickable = true
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(R.color.panel_raised, radiusDp = 16, strokeColorId = R.color.accent))
                addState(intArrayOf(), rounded(R.color.panel, radiusDp = 16))
            }
            setPadding(dp(8), dp(8), dp(8), dp(12))
            growOnFocus()
            setOnClickListener { pick(pkg) }
        }
        tile.addView(artwork(pkg, name), LinearLayout.LayoutParams(-1, dp(TILE_WIDTH * 9 / 16)))
        tile.addView(label(name, sizeSp = 17f, weight = Weight.MEDIUM).apply { singleLine() }.margins(this, top = 10).also { it.setPadding(dp(6), 0, dp(6), 0) })
        tile.addView(label(note.ifEmpty { " " }, sizeSp = 14f, colorId = R.color.muted).apply { singleLine() }.also { it.setPadding(dp(6), 0, dp(6), 0) })
        return tile
    }

    /** The app's TV banner when it has one, otherwise its icon centered on a plain panel. */
    private fun artwork(pkg: String, name: String): View {
        val frame = FrameLayout(this).apply {
            background = rounded(R.color.track, radiusDp = 10)
            clipToOutline = true
        }
        val banner = loadBanner(pkg)
        val image = ImageView(this).apply {
            contentDescription = name
            if (banner != null) {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(banner)
            } else {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull())
            }
        }
        val size = if (banner != null) FrameLayout.LayoutParams(-1, -1) else FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER)
        frame.addView(image, size)
        return frame
    }

    private fun loadBanner(pkg: String): Drawable? = runCatching {
        packageManager.getApplicationBanner(pkg)
            ?: packageManager.getLeanbackLaunchIntentForPackage(pkg)?.let { packageManager.getActivityBanner(it) }
    }.getOrNull()

    private fun TextView.singleLine() {
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun pick(packageName: String) {
        val limits = LimitsRepository.get(this)
        val id = Limit.appLimitId(packageName)
        if (limits.find(id) == null) limits.upsert(Limit.newAppLimit(packageName))
        startActivity(ScheduleEditorActivity.intent(this, id))
        finish()
    }

    companion object {
        private const val COLUMNS = 4
        private const val TILE_WIDTH = 188

        fun launchablePackages(pm: PackageManager, ownPackage: String): Set<String> =
            listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                .map { it.activityInfo.packageName }
                .filter { it != ownPackage }
                .toSet()
    }
}
