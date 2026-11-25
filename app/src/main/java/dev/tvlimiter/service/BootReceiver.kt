package dev.tvlimiter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts tracking after a reboot or an app update. Today's usage comes back from the saved file. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                TrackingService.start(context)
                TubeGuard.schedule(context)
            }
        }
    }
}
