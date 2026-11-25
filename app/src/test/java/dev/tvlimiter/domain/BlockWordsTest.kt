package dev.tvlimiter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlockWordsTest {
    @Test
    fun `a category over the limit says what the video has, in words a child knows`() {
        assertEquals("This video has a lot of scary or creepy parts.", BlockWords.reason(OverKind.VALUE, "scary", "a_lot"))
        assertEquals("This video has some bad language.", BlockWords.reason(OverKind.VALUE, "language", "some"))
        assertEquals("This video has some fighting, weapons or blood.", BlockWords.reason(OverKind.VALUE, "violence", "mild"))
        assertEquals("This video has grown-up themes.", BlockWords.reason(OverKind.VALUE, "mature", null))
    }

    @Test
    fun `every category has a sentence`() {
        listOf("scary", "violence", "sexual", "gross", "kids_characters_adult", "stuff", "pranks", "stunts", "gambling", "language", "drugs", "mature")
            .forEach { assertFalse(it, BlockWords.reason(OverKind.VALUE, it, "a_lot") == "This video is blocked.") }
    }

    @Test
    fun `a switched off topic names the topic`() {
        assertEquals("This video is about politics, which is turned off.", BlockWords.reason(OverKind.WORLDVIEW, "politics", null))
        assertEquals("This video is about gender and sexuality, which is turned off.", BlockWords.reason(OverKind.WORLDVIEW, "gender_sexuality", null))
    }

    @Test
    fun `bans and the other kinds have their own sentence`() {
        assertEquals("This channel is blocked.", BlockWords.reason(OverKind.CHANNEL_BAN, null, null))
        assertEquals("This video is blocked.", BlockWords.reason(OverKind.VIDEO_BAN, null, null))
        assertEquals("This video is a lot like one that is blocked.", BlockWords.reason(OverKind.LEARNED_MATCH, null, null))
        assertEquals("This video is only for older viewers.", BlockWords.reason(OverKind.AGE_RESTRICTED, null, null))
    }

    @Test
    fun `an unknown category or kind falls back to a plain sentence`() {
        assertEquals("This video is blocked.", BlockWords.reason(OverKind.VALUE, "something_new", "a_lot"))
        assertEquals("This video is blocked.", BlockWords.reason(OverKind.OTHER, null, null))
        assertEquals("This video is blocked.", BlockWords.reason(null, null, null))
    }

    @Test
    fun `the parent's reason text never reaches the child`() {
        // The words are built from the kind, value and level alone; the wire outcome has no field for the parent's text.
        val outcome = OutcomeWire.parse(
            """{"outcome":{"decided":true,"outcome":"over","kind":"value","value":"scary","level":"a_lot",
                "reason":"Scary or creepy: a lot, over this child's limit.","evidence":"A monster jumps out."}}""",
        )!!
        val text = BlockWords.reason(outcome)
        assertFalse(text.contains("monster"))
        assertFalse(text.contains("limit"))
    }
}
