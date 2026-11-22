package dev.tvlimiter.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import dev.tvlimiter.BuildConfig

/** A quiet button at the bottom of the home screen that opens the donation QR code. */
internal object Donate {
    fun addTo(activity: Activity, content: LinearLayout) {
        if (BuildConfig.DONATE_URL.isEmpty()) return
        content.addView(
            activity.tvButton("Support TV Limiter") {
                activity.startActivity(Intent(activity, DonateActivity::class.java))
            }.margins(activity, top = 32),
        )
    }
}
