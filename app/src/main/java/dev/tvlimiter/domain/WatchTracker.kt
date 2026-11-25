package dev.tvlimiter.domain

/** A point in time on both clocks: [elapsedMs] cannot be changed from Settings, [wallMs] can. */
data class Moment(val elapsedMs: Long, val wallMs: Long)

/** What YouTube's media session says is playing right now. */
data class Playing(
    val title: String,
    val channel: String,
    val lengthMs: Long,
    /** The item's still picture, read only while it has no title and could be a Short. */
    val art: ShortArt? = null,
) {
    /** No title, a length a Short can have, and a picture to tell it apart: a Short once it has played long enough. */
    val couldBeShort: Boolean get() = title.isBlank() && art != null && Shorts.couldBeShort(lengthMs)
}

/**
 * One video the child watched, ready to be reported. [start] is when it first played. A [correction] is a
 * watch that was already reported, now with a longer length: the first report was an ad's.
 */
data class Watch(
    val title: String,
    val channel: String,
    val durationSec: Int?,
    val start: Moment,
    val correction: Boolean = false,
    /** The TV Limiter profile that was active when the watch started, or null when none was. */
    val profileId: String? = null,
    /** Set for a YouTube Short, which has no title or channel: its still picture. */
    val short: ShortArt? = null,
) {
    val isShort: Boolean get() = short != null
}

/**
 * Turns what YouTube's media session says into watches. It is fed the current state every few
 * seconds and when anything changes, and it hands back each watch once, as soon as it is worth reporting.
 *
 * - An item at least [Rules.longMs] long (a video, or a long ad) is reported once it has played
 *   [Rules.minPlayMs], so a backend can act on it within seconds.
 * - Anything shorter (Shorts, short videos, most pre-roll ads) is reported once it has been quiet for
 *   [Rules.shortQuietMs], and after the same [Rules.minPlayMs] of play, so skipping through reports nothing.
 * - YouTube plays ads under the video's own title with short lengths of their own. Everything with
 *   the same title and channel is one watch, and it keeps the longest length seen.
 * - Coming back to the same title and channel within [Rules.joinGapMs] (a pause, a replay, ads
 *   between parts) continues the same watch, so it is reported once.
 * - If the first report carried an ad's length, a length at least [Rules.correctionMs] longer for the same
 *   watch is handed back once more as a correction, at most [Rules.maxCorrections] times. A mid-roll ad is
 *   shorter than its video, so it never causes one.
 * - An item with no title, [Shorts.MAX_LENGTH_MS] long or less, that has played [Rules.minPlayMs] and is still untitled
 *   is a Short. Each Short (its length and picture) is one watch, reported as soon as it has played that long and
 *   not when it ends, since Shorts play back to back. A video whose title arrives within that time is not a Short.
 * - Nothing that plays while [update] says the report is suppressed (parent mode, or not paired) counts.
 */
class WatchTracker(private val rules: Rules = Rules()) {
    data class Rules(
        val minPlayMs: Long = 5_000,
        val joinGapMs: Long = 60_000,
        /** Items shorter than this wait for [shortQuietMs] of quiet before they are reported. */
        val longMs: Long = 60_000,
        val shortQuietMs: Long = 10_000,
        /** How much longer a length must be than the reported one to correct it. */
        val correctionMs: Long = 3_000,
        val maxCorrections: Int = 2,
        /** The most play one update can add, so a long silence is never counted as viewing. */
        val maxCreditMs: Long = 15_000,
    )

    private class Open(
        val key: String,
        val title: String,
        val channel: String,
        val start: Moment,
        val profileId: String?,
        val art: ShortArt? = null,
    ) {
        var lastPlayedMs = start.elapsedMs
        var playedMs = 0L

        /** Play of items that are at least [Rules.longMs] long. */
        var longPlayedMs = 0L

        /** The length of the item that played at the last update. */
        var itemMs = 0L
        var longestMs = 0L
        var reported = false
        var reportedMs = 0L
        var corrections = 0
    }

    private val open = LinkedHashMap<String, Open>()
    private var current: Open? = null
    private var lastUpdateMs: Long? = null

    /** [playing] is null when nothing is playing, including when the video is paused. */
    fun update(now: Moment, playing: Playing?, suppressed: Boolean = false, profileId: String? = null): List<Watch> {
        creditPlay(now.elapsedMs)
        lastUpdateMs = now.elapsedMs
        val video = playing?.takeIf { it.title.isNotBlank() }
        val short = playing?.takeIf { it.couldBeShort }
        if (suppressed) {
            // What was being played while a parent was watching is not the child's.
            current?.let { if (!it.reported) open.remove(it.key) }
            current = null
        } else if (video == null && short == null) {
            current = null
        } else if (video == null) {
            val art = short!!.art!!
            val key = "short\u0000${seconds(short.lengthMs)}\u0000${art.key}"
            val watch = open.getOrPut(key) { Open(key, "", "", now, profileId, art) }
            watch.longestMs = maxOf(watch.longestMs, short.lengthMs)
            watch.itemMs = short.lengthMs
            watch.lastPlayedMs = now.elapsedMs
            current = watch
        } else {
            val key = keyOf(video!!.title, video.channel)
            val watch = open.getOrPut(key) { Open(key, video.title.trim(), video.channel.trim(), now, profileId) }
            watch.longestMs = maxOf(watch.longestMs, video.lengthMs)
            watch.itemMs = video.lengthMs
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
        val credit = (nowMs - since).coerceIn(0, rules.maxCreditMs)
        watch.playedMs += credit
        if (watch.itemMs >= rules.longMs) watch.longPlayedMs += credit
        watch.lastPlayedMs = nowMs
    }

    private fun collect(nowMs: Long, everything: Boolean): List<Watch> {
        val out = mutableListOf<Watch>()
        val iterator = open.values.iterator()
        while (iterator.hasNext()) {
            val w = iterator.next()
            val idleMs = if (w === current) 0 else nowMs - w.lastPlayedMs
            val over = w !== current && idleMs >= rules.joinGapMs
            if (!w.reported) {
                val quietFor = if (w.longestMs >= rules.longMs) rules.joinGapMs else rules.shortQuietMs
                // A Short is reported after the same play as a long video, since it would be over before it went quiet.
                val soon = if (w.art != null) w.playedMs >= rules.minPlayMs else w.longPlayedMs >= rules.minPlayMs
                val due = everything || soon || (w !== current && idleMs >= quietFor)
                if (w.playedMs >= rules.minPlayMs && due) {
                    w.reported = true
                    w.reportedMs = w.longestMs
                    out += Watch(w.title, w.channel, seconds(w.longestMs), w.start, profileId = w.profileId, short = w.art)
                }
            } else if (w.art == null && w.corrections < rules.maxCorrections && w.longestMs >= w.reportedMs + rules.correctionMs) {
                w.corrections++
                w.reportedMs = w.longestMs
                out += Watch(w.title, w.channel, seconds(w.longestMs), w.start, correction = true, profileId = w.profileId)
            }
            if (over) iterator.remove()
        }
        return out.sortedBy { it.start.elapsedMs }
    }

    private fun keyOf(title: String, channel: String) = "${title.trim().lowercase()}\u0000${channel.trim().lowercase()}"

    private fun seconds(ms: Long): Int? = if (ms > 0) ((ms + 500) / 1000).toInt() else null
}
