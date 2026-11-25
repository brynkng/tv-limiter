package dev.tvlimiter.domain

import kotlinx.serialization.Serializable

/** Title, channel and length, written so the same video always compares equal however YouTube cases it. */
data class VideoKey(val title: String, val channel: String, val lengthSec: Int?) {
    /** Same title and channel, and a length within [LENGTH_SLACK_SEC] (or both unknown). */
    fun sameVideo(other: VideoKey): Boolean = title == other.title && channel == other.channel && sameLength(other.lengthSec)

    fun sameChannel(other: VideoKey): Boolean = channel == other.channel

    /** A YouTube Short, which has no channel of its own to allow. */
    val isShort: Boolean get() = channel.startsWith(SHORT_CHANNEL_PREFIX)

    private fun sameLength(other: Int?): Boolean = when {
        lengthSec == null || other == null -> lengthSec == other
        else -> kotlin.math.abs(lengthSec - other) <= LENGTH_SLACK_SEC
    }

    companion object {
        const val LENGTH_SLACK_SEC = 3

        fun of(title: String, channel: String, lengthSec: Int?) = VideoKey(normalize(title), normalize(channel), lengthSec?.takeIf { it > 0 })

        fun of(playing: Playing) = of(playing.title, playing.channel, if (playing.lengthMs > 0) ((playing.lengthMs + 500) / 1000).toInt() else null)

        fun of(report: QueuedReport) =
            if (report.short) ofShort(report.durationSec, report.shortKey.orEmpty()) else of(report.title, report.channel, report.durationSec)

        /** A Short has no title or channel, so it is known by its length and its picture's hash. */
        fun ofShort(lengthSec: Int?, artKey: String) = VideoKey("youtube short", "$SHORT_CHANNEL_PREFIX$artKey", lengthSec?.takeIf { it > 0 })

        fun ofShort(playing: Playing) =
            ofShort(if (playing.lengthMs > 0) ((playing.lengthMs + 500) / 1000).toInt() else null, playing.art?.key.orEmpty())

        const val SHORT_CHANNEL_PREFIX = "short:"

        private fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}

@Serializable
data class CachedBlock(
    val title: String,
    val channel: String,
    val lengthSec: Int?,
    val watchEventId: String?,
    val kind: String,
    val value: String?,
    val level: String?,
    val decidedAtMs: Long,
    val ageBand: String? = null,
) {
    val key: VideoKey get() = VideoKey(title, channel, lengthSec)
    val overKind: OverKind get() = OverKind.entries.firstOrNull { it.name == kind } ?: OverKind.OTHER
}

interface BlockCacheStore {
    fun load(): List<CachedBlock>
    fun save(entries: List<CachedBlock>)
}

/**
 * The outcomes of videos already found over a limit, kept for [TTL_MS] so playing one again blocks at once, without
 * waiting for TubeGuard. Only blocks are kept: a video that is fine is not remembered, so a parent's new ban is
 * never hidden by an old answer.
 */
class BlockCache(private val store: BlockCacheStore, private val nowMs: () -> Long, private val ttlMs: Long = TTL_MS) {
    private val lock = Any()

    fun find(key: VideoKey): CachedBlock? = synchronized(lock) { fresh().lastOrNull { it.key.sameVideo(key) } }

    fun put(key: VideoKey, outcome: WatchOutcome, watchEventId: String?) = synchronized(lock) {
        val entry = CachedBlock(
            title = key.title,
            channel = key.channel,
            lengthSec = key.lengthSec,
            watchEventId = watchEventId,
            kind = (outcome.kind ?: OverKind.OTHER).name,
            value = outcome.value,
            level = outcome.level,
            decidedAtMs = nowMs(),
            ageBand = outcome.ageBand,
        )
        store.save((fresh().filterNot { it.key.sameVideo(key) } + entry).takeLast(MAX_ENTRIES))
    }

    fun remove(key: VideoKey) = synchronized(lock) {
        val kept = fresh().filterNot { it.key.sameVideo(key) }
        store.save(kept)
    }

    private fun fresh(): List<CachedBlock> {
        val limit = nowMs() - ttlMs
        return store.load().filter { it.decidedAtMs > limit }
    }

    companion object {
        const val TTL_MS = 24 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 200
    }
}

/**
 * What a parent allowed with the passkey. It lives only in memory, so it ends when the TV sleeps or the app restarts,
 * as parent mode does.
 */
class Allowances {
    private val videos = mutableListOf<VideoKey>()
    private val channels = mutableSetOf<String>()

    @Synchronized fun allowVideo(key: VideoKey) {
        videos += key
    }

    @Synchronized fun allowChannel(key: VideoKey) {
        channels += key.channel
    }

    @Synchronized fun allows(key: VideoKey): Boolean = key.channel in channels || videos.any { it.sameVideo(key) }

    @Synchronized fun clear() {
        videos.clear()
        channels.clear()
    }

    companion object {
        /** The one set shared by the block screen and the listener. */
        val shared = Allowances()
    }
}
