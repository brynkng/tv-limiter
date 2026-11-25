package dev.tvlimiter.ui

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import dev.tvlimiter.R
import dev.tvlimiter.domain.NotificationAccessGuide

/** Whether the permissions TV Limiter uses are granted, and how to grant them. */
object PermissionStatus {
    fun usageAdb(context: Context) = "adb shell appops set ${context.packageName} GET_USAGE_STATS allow"
    fun overlayAdb(context: Context) = "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun usageAccessScreen(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayScreen(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /**
     * The notification listener that follows YouTube. A build with no TubeGuard address has none. Debug builds use
     * the detection recorder, which extends the real one, so the access granted to it before still works.
     */
    private fun listener(context: Context) = ComponentName(context.packageName, context.getString(R.string.tubeguard_listener_class))

    fun hasNotificationListener(context: Context): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(listener(context), 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun hasNotificationAccess(context: Context): Boolean {
        val component = listener(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component) == true
        }
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun notificationAccessScreen(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appsScreen(): Intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)

    fun notificationAccessAdb(context: Context) =
        "adb shell cmd notification allow_listener ${listener(context).flattenToString()} 0"

    fun notificationAccessGuide(context: Context): NotificationAccessGuide {
        val pm = context.packageManager
        val opens = { intent: Intent -> intent.resolveActivity(pm) != null }
        return NotificationAccessGuide.forDevice(
            NotificationAccessGuide.Device(
                sdkInt = Build.VERSION.SDK_INT,
                isFireTv = pm.hasSystemFeature("amazon.hardware.fire_tv") || Build.MANUFACTURER.equals("Amazon", ignoreCase = true),
                isLowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true,
                accessScreenOpens = opens(notificationAccessScreen()),
                appsScreenOpens = opens(appsScreen()),
            ),
        )
    }
}
