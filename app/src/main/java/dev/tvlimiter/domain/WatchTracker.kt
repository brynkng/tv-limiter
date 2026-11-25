package dev.tvlimiter.domain

import kotlinx.serialization.Serializable

/** A point in time on both clocks: [elapsedMs] cannot be changed from Settings, [wallMs] can. */
data class Moment(val elapsedMs: Long, val wallMs: Long)

/** A stretch of a video that was played, in whole seconds from its start. [endSec] is not included. */
@Serializable
data class Span(val startSec: Int, val endSec: Int)

/** What was played of one watch, to tell TubeGuard. [start] names the watch, as in [Watch.start]. */
data class SpanUpdate(val start: Moment, val spans: List<Span>, val positionSec: Int? = null)

/** What YouTube's media session says is playing right now. */
data class Playing(
    val title: String,
    val channel: String,
    val lengthMs: Long,
    /** The item's still picture, read only while it has no title and could be a Short. */
    val art: ShortArt? = null,
    /** Where in the item the player is now, in milliseconds, or null when the session doesn't say (see [Positions]). */
    val positionMs: Long? = null,
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
    /** What has been played of the video so far, or null when the session never gave a position (the backend then rates from the start). */
    val spans: List<Span>? = null,
    /** Where the player was when this was handed back, in whole seconds, or null when the session gave no position. */
    val positionSec: Int? = null,
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
 * - What part of a video was played is kept as spans of seconds, from the player's position at each update.
 *   Only play while the item's length is the video's own length counts, so an ad's positions never do, and
 *   spans played under a shorter length are dropped once the real length shows. A pause, a jump back or a jump
 *   forward starts a new span. A watch's spans go out with its first report, then in a [SpanUpdate] about every
 *   [Rules.spanEveryMs] of play, when the watch has played [Rules.minuteSec] in all, and when it ends.
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
        /** How often the spans of a watch in progress are sent again. */
        val spanEveryMs: Long = 30_000,
        /** Played seconds at which the backend starts rating a video, so the spans are sent as soon as it is reached. */
        val minuteSec: Int = 60,
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

        /** Positions played, in milliseconds: [start, end] pairs, the last one still growing. */
        val played = mutableListOf<LongArray>()

        /** The length the spans were played under. */
        var spanLengthMs = 0L
        var lastPositionMs: Long? = null
        var lastPositionAtMs = 0L
        var positionSeen = false

        /** The latest position played under the video's own length, kept through pauses. */
        var currentMs: Long? = null
        var sentSpans: List<Span>? = null
        var sentAtMs = 0L
        var sentMinute = false
    }

    private val open = LinkedHashMap<String, Open>()
    private var current: Open? = null
    private var lastUpdateMs: Long? = null
    private val pendingSpans = mutableListOf<SpanUpdate>()

    /** The span updates that are due, once each. Call it after [update] and [finish]. */
    fun takeSpanUpdates(): List<SpanUpdate> = pendingSpans.toList().also { pendingSpans.clear() }

    private fun setCurrent(next: Open?) {
        if (current !== next) current?.lastPositionMs = null
        current = next
    }

    /** [playing] is null when nothing is playing, including when the video is paused. */
    fun update(now: Moment, playing: Playing?, suppressed: Boolean = false, profileId: String? = null): List<Watch> {
        creditPlay(now.elapsedMs)
        lastUpdateMs = now.elapsedMs
        val video = playing?.takeIf { it.title.isNotBlank() }
        val short = playing?.takeIf { it.couldBeShort }
        if (suppressed) {
            // What was being played while a parent was watching is not the child's.
            current?.let { if (!it.reported) open.remove(it.key) }
            setCurrent(null)
        } else if (video == null && short == null) {
            setCurrent(null)
        } else if (video == null) {
            val art = short!!.art!!
            val key = "short\u0000${seconds(short.lengthMs)}\u0000${art.key}"
            val watch = open.getOrPut(key) { Open(key, "", "", now, profileId, art) }
            watch.longestMs = maxOf(watch.longestMs, short.lengthMs)
            watch.itemMs = short.lengthMs
            watch.lastPlayedMs = now.elapsedMs
            setCurrent(watch)
        } else {
            val key = keyOf(video!!.title, video.channel)
            val watch = open.getOrPut(key) { Open(key, video.title.trim(), video.channel.trim(), now, profileId) }
            watch.longestMs = maxOf(watch.longestMs, video.lengthMs)
            watch.itemMs = video.lengthMs
            watch.lastPlayedMs = now.elapsedMs
            setCurrent(watch)
            recordPosition(watch, now.elapsedMs, video)
        }
        val reports = collect(now.elapsedMs, everything = false)
        queueSpanUpdates(now.elapsedMs)
        return reports
    }

    /** Reports what has played enough, without waiting for it to go quiet. For when listening stops. */
    fun finish(now: Moment): List<Watch> {
        creditPlay(now.elapsedMs)
        setCurrent(null)
        lastUpdateMs = null
        val reports = collect(now.elapsedMs, everything = true)
        open.values.forEach { endOf(it) }
        open.clear()
        return reports
    }

    /** Notes where the player is, as a stretch of the video. Only play of the video's own length counts. */
    private fun recordPosition(w: Open, nowMs: Long, p: Playing) {
        val pos = p.positionMs
        if (pos == null || p.lengthMs <= 0) {
            w.lastPositionMs = null
            return
        }
        if (p.lengthMs >= w.spanLengthMs + rules.correctionMs) {
            // A longer length than the one the spans were played under: they were an ad's.
            w.played.clear()
            w.spanLengthMs = p.lengthMs
            w.lastPositionMs = null
        } else if (p.lengthMs < w.spanLengthMs - rules.correctionMs) {
            // An ad inside the video, with a length of its own.
            w.lastPositionMs = null
            return
        }
        val previous = w.lastPositionMs
        val gapMs = nowMs - w.lastPositionAtMs
        w.lastPositionMs = pos
        w.lastPositionAtMs = nowMs
        w.positionSeen = true
        w.currentMs = pos
        val last = w.played.lastOrNull()
        // Playing on from the last position: at most twice as fast as the clock, and not backwards past a little noise.
        val continues = previous != null && last != null && gapMs <= rules.maxCreditMs && pos >= previous - BACKWARD_NOISE_MS && pos - previous <= gapMs * 2 + FORWARD_SLACK_MS
        if (continues) last!![1] = maxOf(last[1], pos) else w.played += longArrayOf(pos, pos)
    }

    private fun spansOf(w: Open): List<Span> {
        val sorted = w.played.map { Span(round(it[0]), round(it[1])) }.sortedWith(compareBy({ it.startSec }, { it.endSec }))
        val out = mutableListOf<Span>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && s.startSec <= last.endSec) out[out.lastIndex] = Span(last.startSec, maxOf(last.endSec, s.endSec)) else out += s
        }
        return out
    }

    private fun positionOf(w: Open): Int? = w.currentMs?.let { round(it) }

    private fun round(ms: Long): Int = ((ms + 500) / 1000).toInt()

    private fun playedSeconds(spans: List<Span>) = spans.sumOf { it.endSec - it.startSec }

    /** Sends what was played of each reported watch once it is due: every [Rules.spanEveryMs], and at [Rules.minuteSec] played. */
    private fun queueSpanUpdates(nowMs: Long) {
        for (w in open.values) {
            if (!w.reported || !w.positionSeen) continue
            val spans = spansOf(w)
            if (spans == w.sentSpans) continue
            val minuteReached = !w.sentMinute && playedSeconds(spans) >= rules.minuteSec
            if (minuteReached || nowMs - w.sentAtMs >= rules.spanEveryMs) markSent(w, spans, nowMs)
        }
    }

    private fun markSent(w: Open, spans: List<Span>, nowMs: Long) {
        w.sentSpans = spans
        w.sentAtMs = nowMs
        if (playedSeconds(spans) >= rules.minuteSec) w.sentMinute = true
        pendingSpans += SpanUpdate(w.start, spans, positionOf(w))
    }

    /** A watch is over: whatever was played since the last update goes out. */
    private fun endOf(w: Open) {
        if (!w.reported || !w.positionSeen) return
        val spans = spansOf(w)
        if (spans != w.sentSpans) markSent(w, spans, w.lastPlayedMs)
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
                    val spans = if (w.art == null && w.positionSeen) spansOf(w) else null
                    spans?.let {
                        w.sentSpans = it
                        w.sentAtMs = nowMs
                        if (playedSeconds(it) >= rules.minuteSec) w.sentMinute = true
                    }
                    out += Watch(w.title, w.channel, seconds(w.longestMs), w.start, profileId = w.profileId, short = w.art, spans = spans, positionSec = if (spans != null) positionOf(w) else null)
                }
            } else if (w.art == null && w.corrections < rules.maxCorrections && w.longestMs >= w.reportedMs + rules.correctionMs) {
                w.corrections++
                w.reportedMs = w.longestMs
                out += Watch(w.title, w.channel, seconds(w.longestMs), w.start, correction = true, profileId = w.profileId, spans = if (w.positionSeen) spansOf(w) else null, positionSec = if (w.positionSeen) positionOf(w) else null)
            }
            if (over) {
                endOf(w)
                iterator.remove()
            }
        }
        return out.sortedBy { it.start.elapsedMs }
    }

    private fun keyOf(title: String, channel: String) = "${title.trim().lowercase()}\u0000${channel.trim().lowercase()}"

    private fun seconds(ms: Long): Int? = if (ms > 0) ((ms + 500) / 1000).toInt() else null

    private companion object {
        /** The player's position can wobble back a little between two reads. */
        const val BACKWARD_NOISE_MS = 1_500L

        /** A jump forward of more than twice the clock plus this is a seek, not play. */
        const val FORWARD_SLACK_MS = 2_000L
    }
}
