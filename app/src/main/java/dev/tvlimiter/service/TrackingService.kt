package dev.tvlimiter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dev.tvlimiter.R
import dev.tvlimiter.data.LimitsRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.LimitEvaluator
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.ui.BlockActivity

/** Polls usage events, charges foreground time, and covers an app with the block screen when it runs out. */
class TrackingService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val accountant = TickAccountant(INTERVAL_MILLIS)
    private var foreground: String? = null
    private var lastQueryEnd = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_DREAMING_STARTED) ParentMode.shared.end()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                onTick()
            } catch (e: Exception) {
                Log.e(TAG, "Tracking tick failed", e)
            }
            handler.postDelayed(this, INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        thread = HandlerThread("tracking").apply { start() }
        handler = Handler(thread.looper)
        handler.post(tick)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_DREAMING_STARTED) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOff, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onTick() {
        val now = System.currentTimeMillis()
        // The first query looks back further so an app already open when the service starts is found.
        val begin = if (lastQueryEnd == 0L) now - FIRST_LOOKBACK_MILLIS else lastQueryEnd - OVERLAP_MILLIS
        foreground = ForegroundResolver.resolve(foreground, queryEvents(begin, now))
        lastQueryEnd = now

        val pkg = foreground
        // Looked up each tick so switching the default launcher takes effect right away.
        val exempt = ExemptPackages(packageName, homePackages(packageManager))
        // A second check on top of the screen-off broadcast, in case that one was missed.
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) ParentMode.shared.end()
        // In parent mode the parent's viewing neither counts nor blocks.
        val chargeable = pkg != null && !exempt.isExempt(pkg) && !ParentMode.shared.active
        val seconds = accountant.secondsToCharge(SystemClock.elapsedRealtime(), chargeable)
        if (pkg == null || !chargeable) return

        val usage = UsageRepository.get(this)
        if (seconds > 0) usage.charge(pkg, seconds)
        val evaluation = LimitEvaluator.evaluate(LimitsRepository.get(this).all(), usage.today(), usage.weekday(), pkg)
        if (evaluation.blocking != null) showBlock(pkg)
    }

    private fun showBlock(pkg: String) {
        // Re-launch whenever the block screen isn't showing, so reopening the app is covered on
        // the next tick. A launch that lands while it is already up is ignored by BlockActivity.
        if (BlockActivity.visible) return
        startActivity(BlockActivity.intent(this, pkg))
    }

    private fun queryEvents(begin: Long, end: Long): List<AppEvent> {
        val manager = getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = manager.queryEvents(begin, end) ?: return emptyList()
        val out = mutableListOf<AppEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                EVENT_RESUMED -> AppEvent.Type.RESUMED
                EVENT_PAUSED -> AppEvent.Type.PAUSED
                else -> continue
            }
            out += AppEvent(event.packageName, type, event.timeStamp)
        }
        return out
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(getString(R.string.tracking_notification))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val INTERVAL_MILLIS = 2_000L
        private const val FIRST_LOOKBACK_MILLIS = 6 * 60 * 60 * 1000L
        private const val OVERLAP_MILLIS = 1_000L
        private const val TAG = "TrackingService"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        // UsageEvents.Event.ACTIVITY_RESUMED and ACTIVITY_PAUSED; the same values as the older
        // MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND, so they work back to API 26.
        private const val EVENT_RESUMED = 1
        private const val EVENT_PAUSED = 2

        /**
         * Starts tracking. Android can refuse when the app is in the background outside the
         * cases it allows (a normal boot is allowed); then tracking starts the next time the
         * settings open, instead of the app crashing.
         */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, TrackingService::class.java))
            } catch (e: IllegalStateException) {
                android.util.Log.w("TrackingService", "Android did not allow tracking to start now", e)
            }
        }

        fun homePackages(pm: PackageManager): Set<String> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val default = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            return ExemptPackages.homes(default) {
                pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                    .map { it.activityInfo.packageName to it.priority }
            }
        }
    }
}
