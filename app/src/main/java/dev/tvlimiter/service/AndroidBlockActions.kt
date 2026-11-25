package dev.tvlimiter.service

import android.content.Context
import dev.tvlimiter.domain.BlockActions
import dev.tvlimiter.domain.BlockedVideo
import dev.tvlimiter.ui.VideoBlockActivity

/** Pauses YouTube through its media session and covers it with [VideoBlockActivity]. Safe to call from any thread. */
class AndroidBlockActions(private val context: Context) : BlockActions {
    override fun pause() {
        WatchListener.running?.pauseYouTube()
    }

    override fun show(video: BlockedVideo) {
        context.startActivity(VideoBlockActivity.intent(context))
    }

    override fun isShowing(): Boolean = VideoBlockActivity.visible
}
