package dev.tvlimiter.detection

/** Writes one JSON object per line without any library, so it also runs in plain JVM tests. */
object JsonLines {
    fun line(fields: List<Pair<String, Any?>>): String {
        val sb = StringBuilder()
        writeObject(sb, fields)
        return sb.toString()
    }

    fun event(event: String, tsMillis: Long, bootCount: Int, elapsedRealtimeMs: Long, fields: List<Pair<String, Any?>>): String =
        line(
            listOf(
                "ts" to tsMillis,
                "boot_count" to bootCount,
                "elapsed_realtime_ms" to elapsedRealtimeMs,
                "event" to event,
            ) + fields,
        )

    private fun writeObject(sb: StringBuilder, fields: List<Pair<String, Any?>>) {
        sb.append('{')
        fields.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(',')
            writeString(sb, k)
            sb.append(':')
            writeValue(sb, v)
        }
        sb.append('}')
    }

    private fun writeValue(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean, is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Float, is Double -> sb.append(if ((v as Number).toDouble().isFinite()) v.toString() else "null")
            is Map<*, *> -> writeObject(sb, v.entries.map { it.key.toString() to it.value })
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
