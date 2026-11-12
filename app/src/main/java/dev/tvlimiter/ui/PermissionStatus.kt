package dev.tvlimiter.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/** Whether the two permissions the limiter depends on are granted, and how to grant them. */
object PermissionStatus {
    const val USAGE_ADB = "adb shell appops set dev.tvlimiter GET_USAGE_STATS allow"
    const val OVERLAY_ADB = "adb shell appops set dev.tvlimiter SYSTEM_ALERT_WINDOW allow"

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
}
