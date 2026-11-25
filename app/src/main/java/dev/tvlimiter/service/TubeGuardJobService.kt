package dev.tvlimiter.service

import android.app.job.JobParameters
import android.app.job.JobService
import java.util.concurrent.Executors

/** Runs TubeGuard's periodic check-in, and the retry of queued reports after a failed upload. */
class TubeGuardJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        runner.execute {
            val done = try {
                // The check-in and the queue both run; either one failing asks for another try.
                val heartbeat = TubeGuard.heartbeatNow(this)
                val reports = TubeGuard.flushNow(this)
                heartbeat && reports
            } catch (e: Exception) {
                false
            }
            // A periodic job runs again by itself; a failed retry already scheduled its own next try.
            jobFinished(params, !done && params.jobId == TubeGuard.JOB_HEARTBEAT)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private companion object {
        val runner = Executors.newSingleThreadExecutor()
    }
}
