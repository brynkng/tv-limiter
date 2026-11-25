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
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.data.ProfilesRepository
import dev.tvlimiter.data.UsageRepository
import dev.tvlimiter.domain.Gate
import dev.tvlimiter.domain.LimitEvaluator
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.domain.Viewer
import dev.tvlimiter.domain.ViewerGate
import dev.tvlimiter.ui.BlockActivity
import dev.tvlimiter.ui.PermissionStatus
import dev.tvlimiter.ui.WhoActivity

/**
 * Polls usage events, charges foreground time, and covers an app with the block screen when it
 * runs out. The time goes to whoever picked on the "Who's watching" screen, and an app opened
 * before anyone picked is covered by that screen. A TV with one profile and no passkey on it
 * never shows that screen.
 */
class TrackingService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val accountant = TickAccountant(INTERVAL_MILLIS)
    private var foreground: String? = null
    private var lastQueryEnd = 0L

    private val screenChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_DREAMING_STARTED -> {
                    ParentMode.shared.end()
                    Viewer.shared.end()
                }
                // Waking up, or the screensaver ending, asks who is watching.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_DREAMING_STOPPED -> {
                    if (getSystemService(PowerManager::class.java)?.isInteractive == false) return
                    if (gate() == Gate.ShowPicker) showPicker(null)
                }
            }
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
        running = this
        startInForeground()
        TubeGuard.schedule(this)
        thread = HandlerThread("tracking").apply { start() }
        handler = Handler(thread.looper)
        handler.post(tick)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenChanges, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenChanges, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = null
        unregisterReceiver(screenChanges)
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
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) {
            ParentMode.shared.end()
            Viewer.shared.end()
        }
        val gate = gate()
        val counted = pkg != null && !exempt.isExempt(pkg)
        // In parent mode the parent's viewing neither counts nor blocks, and nothing counts
        // before someone picks who is watching.
        val profileId = (gate as? Gate.CountFor)?.profileId
        val seconds = accountant.secondsToCharge(SystemClock.elapsedRealtime(), counted && profileId != null)
        if (pkg == null || !counted) return
        if (gate == Gate.ShowPicker) return showPicker(pkg)
        if (profileId == null) return

        val usage = UsageRepository.get(this, profileId)
        if (seconds > 0) usage.charge(pkg, seconds)
        val evaluation = LimitEvaluator.evaluate(LimitsRepository.get(this, profileId).all(), usage.today(), usage.weekday(), pkg)
        if (evaluation.blocking != null) showBlock(pkg, profileId)
    }

    private fun gate(): Gate {
        val profiles = ProfilesRepository.get(this).profiles()
        return ViewerGate.decide(Viewer.shared.current, ParentMode.shared.active, profiles) { PinRepository.guard(this, it).hasPin() }
    }

    private fun showBlock(pkg: String, profileId: String) {
        // Re-launch whenever the block screen isn't showing, so reopening the app is covered on
        // the next tick. A launch that lands while it is already up is ignored by BlockActivity.
        if (BlockActivity.visible) return
        startActivity(BlockActivity.intent(this, pkg, profileId))
    }

    /** Covers [pkg] (or the home screen, when null) with the picker unless it is already up. */
    private fun showPicker(pkg: String?) {
        if (WhoActivity.visible) return
        startActivity(WhoActivity.intent(this, pkg))
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
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        /**
         * The ongoing notification. While this TV is paired with TubeGuard and notification access is on, it also says
         * the TV is monitored by the family's parent app, as Google Play's rules for monitoring apps ask.
         */
        private fun buildNotification(context: Context): Notification {
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(context.getString(R.string.tracking_notification))
                .setOngoing(true)
            if (TubeGuard.isConnected(context) && PermissionStatus.hasNotificationAccess(context)) {
                builder.setContentText(context.getString(R.string.monitored_notification))
            }
            return builder.build()
        }

        @Volatile private var running: TrackingService? = null

        /**
         * Updates the notification's text after pairing, disconnecting, or notification access changing. It starts
         * the foreground notification again rather than posting a new one, so it needs no notification permission.
         */
        fun refreshNotice(context: Context) {
            running?.startInForeground()
        }

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
