package dev.tvlimiter.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonLinesTest {
    @Test
    fun keepsFieldOrderAndTypes() {
        val line = JsonLines.line(listOf("a" to 1, "b" to "x", "c" to true, "d" to null, "e" to 2.5))
        assertEquals("""{"a":1,"b":"x","c":true,"d":null,"e":2.5}""", line)
    }

    @Test
    fun escapesQuotesBackslashesNewlinesAndControlCharacters() {
        val line = JsonLines.line(listOf("t" to "say \"hi\"\\ok\nnext\u0001"))
        assertEquals("""{"t":"say \"hi\"\\ok\nnext\u0001"}""", line)
    }

    @Test
    fun keepsNonAsciiText() {
        assertEquals("""{"t":"café 日本"}""", JsonLines.line(listOf("t" to "café 日本")))
    }

    @Test
    fun writesNestedMapsAndLists() {
        val line = JsonLines.line(listOf("m" to linkedMapOf("k" to listOf(1, "two"), "z" to emptyMap<String, Any?>())))
        assertEquals("""{"m":{"k":[1,"two"],"z":{}}}""", line)
    }

    @Test
    fun envelopePutsTimeBootCountAndEventFirst() {
        val line = JsonLines.event("metadata", 1700000000000L, 7, 123456L, listOf("package" to "com.google.android.youtube"))
        assertEquals(
            """{"ts":1700000000000,"boot_count":7,"elapsed_realtime_ms":123456,"event":"metadata","package":"com.google.android.youtube"}""",
            line,
        )
    }
}
