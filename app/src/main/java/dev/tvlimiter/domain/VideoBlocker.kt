package dev.tvlimiter.domain

/** The video the block screen is covering. [watchEventId] is TubeGuard's id for the watch, when it is known. */
data class BlockedVideo(val key: VideoKey, val kind: OverKind, val value: String?, val level: String?, val watchEventId: String?) {
    val reason: String get() = BlockWords.reason(kind, value, level)
}

/** What the blocker does to the TV. Implemented against the real media session and screen, and by fakes in tests. */
interface BlockActions {
    /** Pauses YouTube. Never "next": that would start a video nobody checked. */
    fun pause()

    /** Covers the paused player with the block screen. */
    fun show(video: BlockedVideo)

    /** Whether the block screen is on screen now. */
    fun isShowing(): Boolean
}

/** Asks TubeGuard about a watch. Blocking: call it off the main thread. */
fun interface OutcomeSource {
    fun fetch(watchEventId: String): Fetch
}

/**
 * Decides when a video the child is watching is blocked.
 *
 * - Only an "over" outcome blocks. Near, within, not yet checked, an unreachable backend and parent mode never do.
 * - A video found over a limit is remembered ([BlockCache]), so playing it again blocks at once.
 * - A watch whose outcome is not decided yet is asked about every [POLL_EVERY_MS] for up to [POLL_FOR_MS], then left alone.
 * - A parent's allowance (this video, or this channel, until the TV sleeps) lifts a block.
 *
 * It is safe to call from the main thread ([onPlaying]) and the worker thread ([onReported], [poll]).
 */
class VideoBlocker(
    private val cache: BlockCache,
    private val allowances: Allowances,
    private val parentMode: () -> Boolean,
    private val source: OutcomeSource,
    private val actions: BlockActions,
    private val pollEveryMs: Long = POLL_EVERY_MS,
    private val pollForMs: Long = POLL_FOR_MS,
) {
    private class Pending(val key: VideoKey, val watchEventId: String, val startedMs: Long) {
        var lastAskedMs: Long? = null
    }

    private val lock = Any()
    private val pending = mutableListOf<Pending>()
    private var current: VideoKey? = null

    /** The video the block screen covers now, or the last one it covered. */
    @Volatile var blocked: BlockedVideo? = null
        private set

    /** Called whenever YouTube's session is read. [playing] is null when nothing is playing, paused included. */
    fun onPlaying(playing: Playing?) {
        if (playing == null) return
        val key = VideoKey.of(playing)
        synchronized(lock) { current = key }
        if (parentMode() || allowances.allows(key)) return
        val hit = cache.find(key) ?: return
        block(key, hit.overKind, hit.value, hit.level, hit.watchEventId)
    }

    /** A report was taken. [outcome] is what the reply carried, null when the video is not decided yet. */
    fun onReported(key: VideoKey, watchEventId: String?, outcome: WatchOutcome?, nowMs: Long) {
        if (outcome != null && outcome.decided) return decided(key, watchEventId, outcome)
        if (watchEventId == null) return
        synchronized(lock) {
            pending.removeAll { it.key.sameVideo(key) }
            pending += Pending(key, watchEventId, nowMs)
        }
    }

    /** Asks about each undecided watch that is due. Run it every few seconds on the worker thread. */
    fun poll(nowMs: Long) {
        val due = synchronized(lock) {
            pending.removeAll { nowMs - it.startedMs > pollForMs }
            pending.filter { p -> p.lastAskedMs?.let { nowMs - it >= pollEveryMs } ?: true }.onEach { it.lastAskedMs = nowMs }
        }
        for (watch in due) {
            val fetched = source.fetch(watch.watchEventId)
            if (fetched is Fetch.Gone) synchronized(lock) { pending.remove(watch) }
            if (fetched !is Fetch.Got || !fetched.outcome.decided) continue
            synchronized(lock) { pending.remove(watch) }
            decided(watch.key, watch.watchEventId, fetched.outcome)
        }
    }

    /** A parent approved or allowed [key]: it is no longer blocked and a replay plays. */
    fun lifted(key: VideoKey) {
        cache.remove(key)
        synchronized(lock) { if (blocked?.key?.sameVideo(key) == true) blocked = null }
    }

    /** The watches still being asked about. For tests. */
    fun pendingCount(): Int = synchronized(lock) { pending.size }

    private fun decided(key: VideoKey, watchEventId: String?, outcome: WatchOutcome) {
        if (!outcome.isOver) {
            // It is fine now, for example after a parent approved it. Forget an earlier block.
            cache.remove(key)
            return
        }
        cache.put(key, outcome, watchEventId)
        if (parentMode() || allowances.allows(key)) return
        // The child may have moved on to another video while it was being checked.
        val playingNow = synchronized(lock) { current }
        if (playingNow != null && !playingNow.sameVideo(key)) return
        block(key, outcome.kind ?: OverKind.OTHER, outcome.value, outcome.level, watchEventId)
    }

    private fun block(key: VideoKey, kind: OverKind, value: String?, level: String?, watchEventId: String?) {
        val video = BlockedVideo(key, kind, value, level, watchEventId)
        val covered = blocked
        blocked = video
        // Pausing again is harmless and stops a video the child started behind the cover.
        actions.pause()
        // A screen already up for another video is told about this one.
        if (!actions.isShowing() || covered?.key?.sameVideo(key) != true) actions.show(video)
    }

    companion object {
        const val POLL_EVERY_MS = 3_000L
        const val POLL_FOR_MS = 90_000L
    }
}
