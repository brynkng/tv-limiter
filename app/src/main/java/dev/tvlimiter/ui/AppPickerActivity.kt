package dev.tvlimiter.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Limit

/** Lists the apps that can be opened from the TV home screen, most used today first. */
class AppPickerActivity : GuardedActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = column().apply { setPadding(dp(48), dp(32), dp(48), dp(32)) }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(color(R.color.bg))
                addView(content)
            },
        )
    }

    override fun onUnlockedResume() {
        content.removeAllViews()
        content.addView(label("Pick an app to limit", sizeSp = 28f, bold = true))
        val usage = UsageRepository.get(this).today()
        val apps = launchablePackages(packageManager, packageName)
            .map { it to BlockActivity.appLabel(packageManager, it) }
            .sortedWith(compareByDescending<Pair<String, String>> { usage.secondsFor(it.first) }.thenBy { it.second.lowercase() })
        if (apps.isEmpty()) content.addView(label("No apps found.", colorId = R.color.muted).margins(this, top = 16))
        apps.forEachIndexed { index, (pkg, name) ->
            val used = usage.secondsFor(pkg)
            val text = if (used > 0) "$name (${formatMinutes(used)} today)" else name
            val button = tvButton(text) { pick(pkg) }
            content.addView(button.margins(this, top = 10))
            if (index == 0) button.requestFocus()
        }
    }

    private fun pick(packageName: String) {
        val limits = LimitsRepository.get(this)
        val id = Limit.appLimitId(packageName)
        if (limits.find(id) == null) limits.upsert(Limit.newAppLimit(packageName))
        startActivity(ScheduleEditorActivity.intent(this, id))
        finish()
    }

    companion object {
        fun launchablePackages(pm: PackageManager, ownPackage: String): Set<String> =
            listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                .map { it.activityInfo.packageName }
                .filter { it != ownPackage }
                .toSet()
    }
}
