package dev.tvlimiter.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import dev.tvlimiter.BuildConfig
import dev.tvlimiter.data.BlockCacheRepository
import dev.tvlimiter.data.FlushResult
import dev.tvlimiter.data.HttpTransport
import dev.tvlimiter.data.ReportQueueRepository
import dev.tvlimiter.data.ReportUploader
import dev.tvlimiter.data.TubeGuardLinkRepository
import dev.tvlimiter.data.TubeGuardTransport
import dev.tvlimiter.domain.Allowances
import dev.tvlimiter.domain.BlockCache
import dev.tvlimiter.domain.Fetch
import dev.tvlimiter.domain.Moment
import dev.tvlimiter.domain.OutcomeWire
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.domain.PairResult
import dev.tvlimiter.domain.TubeGuardWire
import dev.tvlimiter.domain.UploadOutcome
import dev.tvlimiter.domain.VideoBlocker
import dev.tvlimiter.domain.VideoKey
import dev.tvlimiter.domain.Watch
import dev.tvlimiter.ui.ApproveResult
import dev.tvlimiter.ui.AskResult
import dev.tvlimiter.ui.PermissionStatus
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything that talks to TubeGuard. With no backend address in the build ([enabled] is false) every
 * call does nothing, so a build without one behaves exactly like TV Limiter did before.
 *
 * Network work runs on one background thread. Nothing here logs a title or the device token.
 */
object TubeGuard {
    private const val TAG = "TubeGuard"
    internal const val JOB_HEARTBEAT = 7001
    private const val JOB_RETRY = 7002
    private const val HEARTBEAT_EVERY_MS = 4 * 60 * 60 * 1000L

    val enabled: Boolean get() = BuildConfig.TUBEGUARD_URL.isNotEmpty()

    private val worker = Executors.newSingleThreadExecutor()
    private val transport: TubeGuardTransport by lazy { HttpTransport(BuildConfig.TUBEGUARD_URL) }
    private var uploader: ReportUploader? = null

    fun now() = Moment(SystemClock.elapsedRealtime(), System.currentTimeMillis())

    fun isConnected(context: Context): Boolean = enabled && TubeGuardLinkRepository.get(context).current().isConnected

    /** Settings.Global.BOOT_COUNT counts boots, which tells the backend when the elapsed clock started over. */
    fun bootCount(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    /** Queues a watch and sends what is queued. Safe to call from any thread. */
    fun submit(context: Context, watch: Watch) {
        if (!isConnected(context)) return
        val app = context.applicationContext
        worker.execute {
            ReportQueueRepository.get(app).add(TubeGuardWire.queued(watch, bootCount(app)))
            flushNow(app)
        }
    }

    /** Sends whatever is queued. Safe to call from any thread. */
    fun flush(context: Context) {
        if (!isConnected(context)) return
        val app = context.applicationContext
        worker.execute { flushNow(app) }
    }

    /** Runs on the worker thread (or a job's own thread). Returns false when it should be tried again. */
    internal fun flushNow(context: Context): Boolean = synchronized(this) {
        if (!isConnected(context)) return true
        val link = TubeGuardLinkRepository.get(context)
        val sender = uploader ?: ReportUploader(
            queue = ReportQueueRepository.get(context),
            transport = transport,
            token = { link.current().deviceToken },
            now = ::now,
            bootCount = { bootCount(context) },
            onSent = { report, watchEventId, outcome ->
                blocker(context).onReported(VideoKey.of(report), watchEventId, outcome, SystemClock.elapsedRealtime())
            },
        ).also { uploader = it }
        try {
            when (val result = sender.flush()) {
                FlushResult.Done -> true
                is FlushResult.Retry -> {
                    scheduleRetry(context, result.afterMs)
                    false
                }
                FlushResult.Disconnected -> {
                    removedByParent(context)
                    true
                }
            }
        } catch (e: Exception) {
            // A disk problem or similar. The report stays queued and the next attempt tries again.
            Log.w(TAG, "Sending reports failed: ${e.javaClass.simpleName}")
            scheduleRetry(context, RETRY_AFTER_ERROR_MS)
            false
        }
    }

    // Blocking ---------------------------------------------------------------------------------------------

    private val blockerLock = Any()
    @Volatile private var blockerInstance: VideoBlocker? = null
    private val polling = AtomicBoolean(false)

    /** Runs the block screen's own calls (asking a parent, polling), so they never queue behind a slow upload. */
    val screenWorker: ExecutorService = Executors.newSingleThreadExecutor()

    /** The one blocker, shared by the media listener (which feeds it what plays) and the block screen. */
    fun blocker(context: Context): VideoBlocker = blockerInstance ?: synchronized(blockerLock) {
        val app = context.applicationContext
        blockerInstance ?: VideoBlocker(
            cache = BlockCache(BlockCacheRepository.get(app), System::currentTimeMillis),
            allowances = Allowances.shared,
            parentMode = { ParentMode.shared.active },
            source = { id -> fetchOutcome(app, id) },
            actions = AndroidBlockActions(app),
        ).also { blockerInstance = it }
    }

    /** Asks about watches whose outcome is not decided yet. Called every few seconds by the media listener. */
    fun pollBlocks(context: Context) {
        if (!isConnected(context)) return
        val blocker = blocker(context)
        if (blocker.pendingCount() == 0 || !polling.compareAndSet(false, true)) return
        worker.execute {
            try {
                blocker.poll(SystemClock.elapsedRealtime())
            } catch (e: Exception) {
                Log.w(TAG, "Checking a video's outcome failed: ${e.javaClass.simpleName}")
            } finally {
                polling.set(false)
            }
        }
    }

    /** What TubeGuard says about one of this TV's watches. Slow: call it off the main thread. Never throws. */
    fun fetchOutcome(context: Context, watchEventId: String): Fetch {
        val token = TubeGuardLinkRepository.get(context).current().deviceToken ?: return Fetch.Unreachable
        return try {
            val reply = transport.get(TubeGuardWire.watchPath(watchEventId), token)
            when {
                reply.status == 404 -> Fetch.Gone
                reply.status in 200..299 -> OutcomeWire.parse(reply.body)?.let { Fetch.Got(it) } ?: Fetch.Unreachable
                else -> Fetch.Unreachable
            }
        } catch (e: IOException) {
            Fetch.Unreachable
        }
    }

    /** Tells the family's parents this TV wants to watch a blocked video. Slow: call it off the main thread. */
    fun requestApproval(context: Context, watchEventId: String): AskResult {
        val token = TubeGuardLinkRepository.get(context).current().deviceToken ?: return AskResult.Unreachable
        return try {
            val reply = transport.post(TubeGuardWire.requestApprovalPath(watchEventId), token, "{}")
            when {
                reply.status in 200..299 -> AskResult.Sent
                reply.status == 429 -> AskResult.TooMany
                reply.status == 404 || reply.status == 409 -> AskResult.Refused
                else -> AskResult.Unreachable
            }
        } catch (e: IOException) {
            AskResult.Unreachable
        }
    }

    /** Records the parent's passkey approval of a video with TubeGuard. Slow: call it off the main thread. */
    fun approveVideo(context: Context, watchEventId: String): ApproveResult {
        val token = TubeGuardLinkRepository.get(context).current().deviceToken ?: return ApproveResult.Unreachable
        return try {
            val reply = transport.post(TubeGuardWire.approvePath(watchEventId), token, "{}")
            when {
                reply.status in 200..299 -> ApproveResult.Recorded
                reply.status == 409 -> ApproveResult.Banned
                else -> ApproveResult.Unreachable
            }
        } catch (e: IOException) {
            ApproveResult.Unreachable
        }
    }

    /** The check-in. Reports whether notification access is still granted. Returns false when it should be retried. */
    internal fun heartbeatNow(context: Context): Boolean = synchronized(this) {
        if (!isConnected(context)) return true
        val token = TubeGuardLinkRepository.get(context).current().deviceToken
        val body = TubeGuardWire.heartbeatBody(PermissionStatus.hasNotificationAccess(context), BuildConfig.VERSION_NAME)
        val outcome = try {
            UploadOutcome.of(transport.post(TubeGuardWire.HEARTBEAT, token, body).status)
        } catch (e: IOException) {
            UploadOutcome.Retry
        }
        when (outcome) {
            UploadOutcome.Disconnected -> removedByParent(context)
            UploadOutcome.Retry -> return false
            else -> Unit
        }
        TrackingService.refreshNotice(context)
        true
    }

    /** Asks the backend to pair with [code]. The slow part: call it off the main thread. */
    fun pair(context: Context, code: String): PairResult {
        // The name the TV's owner gave it in its settings ("Living Room TV"), or a plain one when it has none.
        val name = Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: "Android TV (${Build.MODEL})"
        val body = TubeGuardWire.pairBody(code, name, TimeZone.getDefault().id, BuildConfig.VERSION_NAME)
        val reply = try {
            transport.post(TubeGuardWire.PAIR, null, body)
        } catch (e: IOException) {
            return PairResult.Unreachable
        }
        return TubeGuardWire.pairResult(reply.status, reply.body)
    }

    /** Saves a new pairing and starts the check-ins. */
    fun connected(context: Context, paired: PairResult.Paired) {
        val app = context.applicationContext
        // Reports queued under an earlier pairing belong to that family.
        ReportQueueRepository.get(app).clear()
        TubeGuardLinkRepository.get(app).connect(paired.deviceId, paired.deviceToken, paired.children)
        schedule(app)
        TrackingService.refreshNotice(app)
        worker.execute { heartbeatNow(app) }
    }

    /** A parent disconnects this TV. Queued reports are dropped: they were never sent, and now they never will be. */
    fun disconnect(context: Context) {
        val app = context.applicationContext
        // Tell the backend too, so the TV leaves the parent app's device list. Best effort: offline, the parent
        // can still remove it there, and the token is forgotten here either way.
        val token = TubeGuardLinkRepository.get(app).current().deviceToken
        if (enabled && !token.isNullOrEmpty()) {
            worker.execute {
                try {
                    transport.post(TubeGuardWire.DISCONNECT, token, "{}")
                } catch (e: IOException) {
                    Log.w(TAG, "Could not tell TubeGuard about the disconnect")
                }
            }
        }
        TubeGuardLinkRepository.get(app).forget()
        ReportQueueRepository.get(app).clear()
        BlockCacheRepository.get(app).save(emptyList())
        cancelJobs(app)
        TrackingService.refreshNotice(app)
    }

    /** The backend said 401: a parent removed this TV. Reporting stops until it is paired again. */
    private fun removedByParent(context: Context) {
        TubeGuardLinkRepository.get(context).markDisconnected()
        ReportQueueRepository.get(context).clear()
        BlockCacheRepository.get(context).save(emptyList())
        cancelJobs(context)
        TrackingService.refreshNotice(context)
    }

    /** Makes sure the periodic check-in is scheduled while paired, and cancels it otherwise. Safe to call often. */
    fun schedule(context: Context) {
        if (!enabled) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!isConnected(context)) return cancelJobs(context)
        if (scheduler.getPendingJob(JOB_HEARTBEAT) != null) return
        safely(scheduler, "check-in") {
            schedule(
                JobInfo.Builder(JOB_HEARTBEAT, jobService(context))
                    .setPeriodic(HEARTBEAT_EVERY_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    private fun scheduleRetry(context: Context, afterMs: Long) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        safely(scheduler, "retry") {
            schedule(
                JobInfo.Builder(JOB_RETRY, jobService(context))
                    .setMinimumLatency(afterMs)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    /** Scheduling is a convenience for the tracking service and the listener. A refusal must never crash them. */
    private fun safely(scheduler: JobScheduler, what: String, block: JobScheduler.() -> Unit) {
        try {
            scheduler.block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not schedule the $what job: ${e.javaClass.simpleName}")
        }
    }

    private fun cancelJobs(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.apply {
            cancel(JOB_HEARTBEAT)
            cancel(JOB_RETRY)
        }
    }

    private fun jobService(context: Context) = ComponentName(context, TubeGuardJobService::class.java)

    private const val RETRY_AFTER_ERROR_MS = 5 * 60_000L
}
