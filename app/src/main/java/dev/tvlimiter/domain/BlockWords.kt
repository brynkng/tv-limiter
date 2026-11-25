package dev.tvlimiter.domain

/**
 * The words a child reads on the block screen. They are built here from the category and level, never taken from
 * TubeGuard's reason text, which is written for parents and can quote what the video shows.
 */
object BlockWords {
    const val HEADLINE = "This video is blocked"
    private const val FALLBACK = "This video is blocked."

    /** What a category is, finishing "This video has a lot of ...". */
    private val categories = mapOf(
        "scary" to "scary or creepy parts",
        "violence" to "fighting, weapons or blood",
        "sexual" to "grown-up content",
        "gross" to "gross or shocking parts",
        "kids_characters_adult" to "kids' characters doing grown-up things",
        "stuff" to "buying and showing off stuff",
        "pranks" to "pranks and mean jokes",
        "stunts" to "risky challenges and stunts",
        "gambling" to "gambling-like games",
        "language" to "bad language",
        "drugs" to "drugs, alcohol or vaping",
        "mature" to "grown-up themes",
    )

    private val topics = mapOf(
        "religion" to "religion",
        "politics" to "politics",
        "gender_sexuality" to "gender and sexuality",
        "news_tragedies" to "news and tragedies",
    )

    fun reason(outcome: WatchOutcome): String = reason(outcome.kind, outcome.value, outcome.level)

    fun reason(kind: OverKind?, value: String?, level: String?): String = when (kind) {
        OverKind.VALUE -> categories[value]?.let { "This video has ${amount(level)}$it." } ?: FALLBACK
        OverKind.WORLDVIEW -> topics[value]?.let { "This video is about $it, which is turned off." } ?: FALLBACK
        OverKind.VIDEO_BAN -> "This video is blocked."
        OverKind.CHANNEL_BAN -> "This channel is blocked."
        OverKind.LEARNED_MATCH -> "This video is a lot like one that is blocked."
        OverKind.AGE_RESTRICTED -> "This video is only for older viewers."
        OverKind.OTHER, null -> FALLBACK
    }

    private fun amount(level: String?): String = when (level) {
        "a_lot" -> "a lot of "
        "some", "mild" -> "some "
        else -> ""
    }
}
