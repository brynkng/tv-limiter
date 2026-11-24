package dev.tvlimiter.detection

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Debug builds only: records what YouTube tells Android while a video plays, one JSON
 * line per event, so we can learn what a TV reveals before building on it. Pull it with
 * `adb shell cat /sdcard/Android/data/io.github.brynkng.tvlimiter/files/detection.jsonl`.
 */
object EventLog {
    const val TAG = "TvLimiterDetection"
    private const val FILE_NAME = "detection.jsonl"
    private val lock = Any()

    fun file(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    fun write(context: Context, event: String, fields: List<Pair<String, Any?>>) {
        val line = JsonLines.event(event, System.currentTimeMillis(), bootCount(context), SystemClock.elapsedRealtime(), fields)
        Log.i(TAG, line)
        synchronized(lock) {
            try {
                file(context).appendText(line + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "could not write log file", e)
            }
        }
    }

    private fun bootCount(context: Context): Int =
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (e: Exception) {
            -1
        }
}
