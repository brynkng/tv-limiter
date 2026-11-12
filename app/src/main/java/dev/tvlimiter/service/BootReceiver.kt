package dev.tvlimiter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts tracking after a reboot. Today's usage comes back from the saved file. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TrackingService.start(context)
    }
}
