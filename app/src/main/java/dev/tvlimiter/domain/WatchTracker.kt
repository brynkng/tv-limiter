package dev.tvlimiter.domain

/** A point in time on both clocks: [elapsedMs] cannot be changed from Settings, [wallMs] can. */
data class Moment(val elapsedMs: Long, val wallMs: Long)

/** What YouTube's media session says is playing right now. */
data class Playing(val title: String, val channel: String, val lengthMs: Long)

/** One video the child watched, ready to be reported. [start] is when it first played. */
data class Watch(val title: String, val channel: String, val durationSec: Int?, val start: Moment)

/**
 * Turns what YouTube's media session says into watches. It is fed the current state every few
 * seconds and when anything changes, and it hands back each watch once.
 *
 * - A video counts after [Rules.minPlayMs] of play, so skipping through videos reports nothing.
 * - YouTube plays ads under the video's own title with short lengths of their own. Everything with
 *   the same title and channel is one watch, and it keeps the longest length seen.
 * - Coming back to the same title and channel within [Rules.joinGapMs] (a pause, a replay, ads
 *   between parts) continues the same watch, so it is reported once.
 * - A watch goes out when it has been quiet for [Rules.joinGapMs], or after [Rules.settleMs] of
 *   viewing, so a long video is reported before it ends. By then the ads are over and the length is known.
 * - Nothing that plays while [update] says the report is suppressed (parent mode, or not paired) counts.
 */
class WatchTracker(private val rules: Rules = Rules()) {
    data class Rules(
        val minPlayMs: Long = 5_000,
        val joinGapMs: Long = 60_000,
        val settleMs: Long = 120_000,
        /** The most play one update can add, so a long silence is never counted as viewing. */
        val maxCreditMs: Long = 15_000,
    )

    private class Open(val key: String, val title: String, val channel: String, val start: Moment) {
        var lastPlayedMs = start.elapsedMs
        var playedMs = 0L
        var longestMs = 0L
        var reported = false
    }

    private val open = LinkedHashMap<String, Open>()
    private var current: Open? = null
    private var lastUpdateMs: Long? = null

    /** [playing] is null when nothing is playing, including when the video is paused. */
    fun update(now: Moment, playing: Playing?, suppressed: Boolean = false): List<Watch> {
        creditPlay(now.elapsedMs)
        lastUpdateMs = now.elapsedMs
        val video = playing?.takeIf { it.title.isNotBlank() }
        if (suppressed) {
            // What was being played while a parent was watching is not the child's.
            current?.let { if (!it.reported) open.remove(it.key) }
            current = null
        } else if (video == null) {
            current = null
        } else {
            val key = keyOf(video.title, video.channel)
            val watch = open.getOrPut(key) { Open(key, video.title.trim(), video.channel.trim(), now) }
            watch.longestMs = maxOf(watch.longestMs, video.lengthMs)
            watch.lastPlayedMs = now.elapsedMs
            current = watch
        }
        return collect(now.elapsedMs, everything = false)
    }

    /** Reports what has played enough, without waiting for it to go quiet. For when listening stops. */
    fun finish(now: Moment): List<Watch> {
        creditPlay(now.elapsedMs)
        current = null
        lastUpdateMs = null
        return collect(now.elapsedMs, everything = true).also { open.clear() }
    }

    private fun creditPlay(nowMs: Long) {
        val watch = current ?: return
        val since = lastUpdateMs ?: return
        watch.playedMs += (nowMs - since).coerceIn(0, rules.maxCreditMs)
        watch.lastPlayedMs = nowMs
    }

    private fun collect(nowMs: Long, everything: Boolean): List<Watch> {
        val out = mutableListOf<Watch>()
        val iterator = open.values.iterator()
        while (iterator.hasNext()) {
            val w = iterator.next()
            val quiet = w !== current && nowMs - w.lastPlayedMs >= rules.joinGapMs
            val due = everything || quiet || nowMs - w.start.elapsedMs >= rules.settleMs
            if (!w.reported && w.playedMs >= rules.minPlayMs && due) {
                w.reported = true
                out += Watch(w.title, w.channel, seconds(w.longestMs), w.start)
            }
            if (quiet) iterator.remove()
        }
        return out.sortedBy { it.start.elapsedMs }
    }

    private fun keyOf(title: String, channel: String) = "${title.trim().lowercase()}\u0000${channel.trim().lowercase()}"

    private fun seconds(ms: Long): Int? = if (ms > 0) ((ms + 500) / 1000).toInt() else null
}
