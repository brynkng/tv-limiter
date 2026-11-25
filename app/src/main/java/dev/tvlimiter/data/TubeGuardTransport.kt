package dev.tvlimiter.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpReply(val status: Int, val body: String)

/** Sends one JSON request to the TubeGuard backend. Throws [IOException] when it can't be reached. */
interface TubeGuardTransport {
    @Throws(IOException::class)
    fun post(path: String, token: String?, body: String): HttpReply

    @Throws(IOException::class)
    fun get(path: String, token: String?): HttpReply
}

/**
 * The real transport. Plain [HttpURLConnection] with timeouts, so there is no library to bring in.
 * It never logs a request: bodies hold video titles and the token is a secret.
 */
class HttpTransport(private val baseUrl: String) : TubeGuardTransport {
    override fun post(path: String, token: String?, body: String): HttpReply = send("POST", path, token, body)

    override fun get(path: String, token: String?): HttpReply = send("GET", path, token, null)

    private fun send(method: String, path: String, token: String?, body: String?): HttpReply {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = body != null
            // No redirects, so the token can't be sent anywhere but the address in the build.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            return HttpReply(status, stream?.use { readCapped(it) } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() < MAX_BODY_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 64 * 1024
    }
}
