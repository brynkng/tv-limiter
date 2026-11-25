package dev.tvlimiter.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.tvlimiter.R
import dev.tvlimiter.data.PinRepository
import dev.tvlimiter.domain.Allowances
import dev.tvlimiter.domain.BlockedVideo
import dev.tvlimiter.domain.Fetch
import dev.tvlimiter.domain.ParentMode
import dev.tvlimiter.domain.PinCheck
import dev.tvlimiter.service.TubeGuard
import dev.tvlimiter.service.WatchListener

/**
 * Covers a paused YouTube video that is over a limit. It looks like [BlockActivity], and the passkey works the
 * same way, but it blocks one video, not an app: the child can pick another video, go home, ask a parent
 * through TubeGuard, or hand the remote to a parent.
 */
class VideoBlockActivity : Activity() {
    private lateinit var model: VideoBlockViewModel
    private val main = Handler(Looper.getMainLooper())
    private lateinit var headline: TextView
    private lateinit var reason: TextView
    private lateinit var status: TextView
    private lateinit var notice: TextView
    private lateinit var choosePanel: LinearLayout
    private lateinit var askButton: View
    private lateinit var parentButton: View
    private lateinit var pad: PinPadView
    private lateinit var allowPanel: LinearLayout
    private lateinit var pickButton: View
    private lateinit var allowChannelButton: View
    private var finishing = false

    private val poll = object : Runnable {
        override fun run() {
            if (finishing) return
            val id = model.video.watchEventId
            if (id != null && model.polling) {
                TubeGuard.screenWorker.execute {
                    val fetched = TubeGuard.fetchOutcome(applicationContext, id)
                    main.post { if (!finishing) onFetched(fetched) }
                }
            }
            main.postDelayed(this, POLL_EVERY_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
        start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The listener may ask again for the video already covered; keep any passkey entry in progress.
        val now = TubeGuard.blocker(this).blocked ?: return
        if (::model.isInitialized && now.key.sameVideo(model.video.key) && now.watchEventId == model.video.watchEventId && now.reason == model.video.reason) return
        start()
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        super.onStop()
        visible = false
        // Pressing Home leaves the video paused behind; the next play of it is covered again.
        if (!isChangingConfigurations) finishScreen()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Back is handled here to keep a blocked video from playing")
    override fun onBackPressed() {
        when (model.step) {
            VideoStep.PIN -> if (!pad.handleBack()) {
                model.cancelPin()
                render()
            }
            else -> pickAnother()
        }
    }

    private fun start() {
        val video = TubeGuard.blocker(this).blocked ?: return finishScreen()
        model = VideoBlockViewModel(
            video = video,
            guard = PinRepository.guard(this),
            allowVideo = { allowVideo(video) },
            allowChannel = { Allowances.shared.allowChannel(video.key) },
            startParentMode = ParentMode.shared::start,
        )
        main.removeCallbacks(poll)
        main.postDelayed(poll, FIRST_POLL_MS)
        render()
    }

    private fun allowVideo(video: BlockedVideo) {
        Allowances.shared.allowVideo(video.key)
    }

    private fun render() {
        if (model.step == VideoStep.RESUME) return resume()
        (pickButton as? TextView)?.text = model.pickAnotherLabel
        allowChannelButton.visibility = if (model.canAllowChannel) View.VISIBLE else View.GONE
        headline.text = model.headline
        reason.text = model.reason
        choosePanel.visibility = if (model.step == VideoStep.PIN || model.step == VideoStep.ALLOW) View.GONE else View.VISIBLE
        pad.visibility = if (model.step == VideoStep.PIN) View.VISIBLE else View.GONE
        allowPanel.visibility = if (model.step == VideoStep.ALLOW) View.VISIBLE else View.GONE
        askButton.visibility = if (model.step == VideoStep.CHOOSE) View.VISIBLE else View.GONE
        parentButton.visibility = if (PinRepository.guard(this).hasPin()) View.VISIBLE else View.GONE
        status.text = when (model.step) {
            VideoStep.CHOOSE -> "Time for something else. If you really want this one, ask a parent."
            VideoStep.ASKED -> "Asked. Waiting for a parent."
            VideoStep.DENIED -> "A parent said no. Pick another video."
            VideoStep.PIN -> "Parent: enter your passkey."
            VideoStep.ALLOW -> "Parent: what should we allow?"
            VideoStep.RESUME -> ""
        }
        notice.text = model.notice.orEmpty()
        notice.visibility = if (model.notice == null) View.GONE else View.VISIBLE
        if (model.step == VideoStep.CHOOSE || model.step == VideoStep.ASKED || model.step == VideoStep.DENIED) {
            if (currentFocus == null || currentFocus?.visibility != View.VISIBLE) pickButton.requestFocus()
        }
        if (model.step == VideoStep.ALLOW) allowPanel.getChildAt(0)?.requestFocus()
    }

    private fun buildViews() {
        headline = label("", sizeSp = 42f, bold = true).apply { gravity = Gravity.CENTER }
        reason = label("", sizeSp = 26f, weight = Weight.MEDIUM).apply { gravity = Gravity.CENTER }
        status = label("", sizeSp = 20f, colorId = R.color.muted).apply { gravity = Gravity.CENTER }
        notice = label("", sizeSp = 18f, colorId = R.color.warn).apply { gravity = Gravity.CENTER }
        val spent = TimeBar(this).apply { exhausted = true }

        pickButton = tvButton("Pick another video") { pickAnother() }
        allowChannelButton = tvButton("Allow this channel until the TV sleeps") { model.allowThisChannel(); render() }.also { it.margins(this@VideoBlockActivity, top = 16) }
        val home = tvButton("Go home") { goHome() }
        askButton = tvButton("Ask a parent") { askParent() }
        parentButton = tvButton("I'm a parent") {
            model.requestPin()
            render()
            pad.open()
        }
        val wrap = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
        choosePanel = column(Gravity.CENTER_HORIZONTAL).apply {
            addView(
                row().apply {
                    gravity = Gravity.CENTER
                    addView(pickButton.margins(this@VideoBlockActivity, end = 20))
                    addView(home)
                },
                wrap(),
            )
            addView(
                row().apply {
                    gravity = Gravity.CENTER
                    addView(askButton.margins(this@VideoBlockActivity, end = 20))
                    addView(parentButton)
                },
                wrap().apply { topMargin = dp(16) },
            )
        }

        pad = PinPadView(this)
        pad.bind(lockoutSeconds = { PinRepository.guard(this).lockoutSecondsLeft() }) { pin ->
            val check = model.checkPin(pin)
            val show = {
                model.applyCheck(check)
                if (check == PinCheck.Ok) render() else pad.showResult(check)
            }
            show
        }

        allowPanel = column(Gravity.CENTER_HORIZONTAL).apply {
            clipChildren = false
            addView(tvButton("Allow this video") { allowThisVideo() })
            addView(allowChannelButton)
            addView(tvButton("Parent mode until the TV sleeps") { model.parentMode(); render() }.also { it.margins(this@VideoBlockActivity, top = 16) })
        }

        val content = column(Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dp(64), dp(48), dp(64), dp(48))
            clipChildren = false
            addView(headline, LinearLayout.LayoutParams(-1, -2))
            addView(spent, LinearLayout.LayoutParams(dp(420), dp(12)).apply { topMargin = dp(28) })
            addView(reason, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            addView(notice, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(choosePanel.also { it.margins(this@VideoBlockActivity, top = 36) })
            addView(pad.also { it.margins(this@VideoBlockActivity, top = 36) })
            addView(allowPanel.also { it.margins(this@VideoBlockActivity, top = 36) })
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(color(R.color.bg))
                clipChildren = false
                addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            },
        )
    }

    private fun askParent() {
        val id = model.video.watchEventId
        if (id == null) {
            model.askResult(AskResult.Refused)
            return render()
        }
        TubeGuard.screenWorker.execute {
            val result = TubeGuard.requestApproval(applicationContext, id)
            main.post {
                if (finishing) return@post
                model.askResult(result)
                render()
            }
        }
    }

    private fun allowThisVideo() {
        val id = model.video.watchEventId
        if (id == null) {
            model.allowThisVideo(ApproveResult.Unreachable)
            return render()
        }
        TubeGuard.screenWorker.execute {
            val result = TubeGuard.approveVideo(applicationContext, id)
            main.post {
                if (finishing) return@post
                model.allowThisVideo(result)
                render()
            }
        }
    }

    private fun onFetched(fetched: Fetch) {
        model.pollResult(fetched)
        render()
    }

    /** The cover comes off the paused player; YouTube is still paused behind it. */
    private fun pickAnother() {
        Toast.makeText(applicationContext, model.pickAnotherHint, Toast.LENGTH_LONG).show()
        finishScreen()
    }

    private fun resume() {
        val key = model.video.key
        val watchEventId = model.video.watchEventId
        TubeGuard.screenWorker.execute {
            TubeGuard.blocker(applicationContext).lifted(key, watchEventId, SystemClock.elapsedRealtime())
        }
        WatchListener.running?.playYouTube()
        finishScreen()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishScreen()
    }

    private fun finishScreen() {
        finishing = true
        main.removeCallbacksAndMessages(null)
        if (!isFinishing) finish()
    }

    companion object {
        private const val FIRST_POLL_MS = 1_500L
        private const val POLL_EVERY_MS = 5_000L

        /** Whether the video block screen is on screen now; read from the listener and worker threads. */
        @Volatile var visible = false
            private set

        fun intent(context: Context): Intent =
            Intent(context, VideoBlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
