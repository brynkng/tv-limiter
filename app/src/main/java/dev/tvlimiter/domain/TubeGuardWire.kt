package dev.tvlimiter.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A watch waiting for upload. It keeps both clocks so its age can be worked out after a reboot. */
@Serializable
data class QueuedReport(
    val title: String,
    val channel: String,
    val durationSec: Int?,
    val startElapsedMs: Long,
    val startWallMs: Long,
    /** Settings.Global.BOOT_COUNT when the video was watched. Elapsed time starts again at every boot. */
    val bootCount: Int,
    /** The TubeGuard child linked to the profile that was active when the watch started, or null. */
    val childId: String? = null,
    /** True when this changes the length of a watch already reported, so the backend corrects it instead of adding one. */
    val correction: Boolean = false,
    /** True for a YouTube Short, which has no title or channel: it carries [image], its still picture in base64, instead. */
    val short: Boolean = false,
    val image: String? = null,
    /** The hash of the Short's picture, to tell it from the next one. Never sent. */
    val shortKey: String? = null,
) {
    /** Which watch this is, among the ones from this boot. A correction and the report it corrects share it. */
    val key: String get() = "$bootCount:$startElapsedMs"
}

/** A report the backend has taken, remembered for a while so a correction can name it. */
@Serializable
data class SentReport(val key: String, val watchEventId: String)

/** What the TubeGuard backend sends back for a pairing request. */
sealed interface PairResult {
    data class Paired(
        val deviceId: String,
        val deviceToken: String,
        val children: List<TubeGuardChild>,
        val shortsBlocked: Boolean = false,
    ) : PairResult {
        override fun toString() = "Paired(children=${children.size})"
    }

    /** The digits don't match a code. */
    data object InvalidCode : PairResult

    /** The right code, but it was already used to pair a TV. */
    data object CodeUsed : PairResult

    /** The right code, but more than 15 minutes old. */
    data object CodeExpired : PairResult

    /** The backend stopped accepting this code after 5 wrong guesses at it. */
    data object TooManyAttempts : PairResult

    /** Too many tries from this network. */
    data object RateLimited : PairResult

    /** The TV could not reach TubeGuard, or it answered with an error. */
    data object Unreachable : PairResult
}

/** What to do with a report, or a check-in, once the backend has answered. */
enum class UploadOutcome {
    Sent,

    /** The backend will never take this one, so it is dropped rather than blocking the ones behind it. */
    Rejected,

    /** Keep it and try again later. */
    Retry,

    /** The token no longer works: a parent removed this TV. */
    Disconnected,
    ;

    companion object {
        fun of(status: Int): UploadOutcome = when {
            status in 200..299 -> Sent
            status == 401 -> Disconnected
            status == 408 || status == 429 || status >= 500 -> Retry
            status in 400..499 -> Rejected
            else -> Retry
        }
    }
}

/** The requests TV Limiter sends to TubeGuard. They carry what the contract needs and nothing else. */
object TubeGuardWire {
    const val PAIR = "/api/pair"
    const val HEARTBEAT = "/api/heartbeat"
    const val REPORTS = "/api/reports"
    const val DISCONNECT = "/api/device/disconnect"
    private const val WATCHES = "/api/device/watches"

    /** A watch's outcome (GET), its approval request (POST) and the passkey's approval (POST). */
    fun watchPath(watchEventId: String) = "$WATCHES/${java.net.URLEncoder.encode(watchEventId, "UTF-8")}"

    fun requestApprovalPath(watchEventId: String) = "${watchPath(watchEventId)}/request-approval"

    fun approvePath(watchEventId: String) = "${watchPath(watchEventId)}/approve"

    const val MAX_TITLE = 500
    const val MAX_CHANNEL = 200
    const val MAX_NAME = 100

    /** Shown to the parent when YouTube gave a title but no channel, which the backend requires. */
    const val UNKNOWN_CHANNEL = "Unknown channel"

    fun pairBody(code: String, deviceName: String, timeZone: String, appVersion: String): String = buildJsonObject {
        put("code", code)
        put("name", deviceName.trim().ifEmpty { "TV" }.take(MAX_NAME))
        put("platform", "tv")
        put("timeZone", timeZone)
        put("appVersion", appVersion)
    }.toString()

    fun heartbeatBody(listenerEnabled: Boolean, appVersion: String): String = buildJsonObject {
        put("listenerEnabled", listenerEnabled)
        put("appVersion", appVersion)
    }.toString()

    /**
     * A report's age is how long ago the video started. Within the boot it was watched in, that is
     * measured on the elapsed clock, which a child can't change. After a reboot the elapsed clock has
     * started over, so the age is the wall clock's guess and the backend marks it approximate.
     */
    fun reportBody(report: QueuedReport, now: Moment, currentBootCount: Int, replaces: String? = null): String {
        val sameBoot = report.bootCount == currentBootCount
        val age = if (sameBoot) now.elapsedMs - report.startElapsedMs else now.wallMs - report.startWallMs
        return buildJsonObject {
            if (report.short) {
                // A Short has no title or channel; its length and a small still picture are all that is known of it.
                put("kind", "short")
                report.image?.let { put("image", it) }
            } else {
                put("title", report.title.take(MAX_TITLE))
                put("channel", report.channel.ifBlank { UNKNOWN_CHANNEL }.take(MAX_CHANNEL))
            }
            put("app", "youtube")
            put("ageMs", age.coerceAtLeast(0))
            put("bootCount", report.bootCount)
            put("currentBootCount", currentBootCount)
            report.durationSec?.let { put("durationSec", it) }
            replaces?.let { put("replaces", it) }
            report.childId?.let { put("childId", it) }
        }.toString()
    }

    /** The id the backend gave a stored watch, or null when the reply doesn't carry one. */
    fun watchEventId(body: String): String? = try {
        Json.parseToJsonElement(body).jsonObject["watchEventId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    fun queued(watch: Watch, bootCount: Int, childId: String? = null) = QueuedReport(
        title = watch.title,
        channel = if (watch.isShort) "" else watch.channel.ifBlank { UNKNOWN_CHANNEL },
        durationSec = watch.durationSec,
        startElapsedMs = watch.start.elapsedMs,
        startWallMs = watch.start.wallMs,
        bootCount = bootCount,
        childId = childId,
        correction = watch.correction,
        short = watch.isShort,
        image = watch.short?.jpegBase64,
        shortKey = watch.short?.key,
    )

    fun pairResult(status: Int, body: String): PairResult = when {
        status == 201 || status == 200 -> parsePaired(body) ?: PairResult.Unreachable
        status == 400 -> when (errorCode(body)) {
            "code_used" -> PairResult.CodeUsed
            "code_expired" -> PairResult.CodeExpired
            "too_many_attempts" -> PairResult.TooManyAttempts
            else -> PairResult.InvalidCode
        }
        status == 429 -> PairResult.RateLimited
        else -> PairResult.Unreachable
    }

    private fun errorCode(body: String): String? = try {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        null
    }

    /** The children a check-in reply lists, or null when the reply doesn't list any (an older backend), so the TV keeps what it has. */
    fun children(body: String): List<TubeGuardChild>? = try {
        childrenIn(Json.parseToJsonElement(body).jsonObject)
    } catch (e: Exception) {
        null
    }

    /** The Shorts setting for the whole TV in a reply, true for Block, or null when the reply doesn't carry it (an older backend). */
    fun shortsBlocked(body: String): Boolean? = try {
        Json.parseToJsonElement(body).jsonObject["shorts"]?.jsonPrimitive?.contentOrNull?.let { it == "block" }
    } catch (e: Exception) {
        null
    }

    private fun childrenIn(root: JsonObject): List<TubeGuardChild>? = (root["children"] as? JsonArray)?.mapNotNull {
        val child = it as? JsonObject ?: return@mapNotNull null
        val id = child["id"]?.jsonPrimitive?.contentOrNull?.takeIf { v -> v.isNotEmpty() } ?: return@mapNotNull null
        val name = child["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        TubeGuardChild(id, name, shortsBlocked = child["shorts"]?.jsonPrimitive?.contentOrNull == "block")
    }

    private fun parsePaired(body: String): PairResult.Paired? = try {
        val root = Json.parseToJsonElement(body).jsonObject
        val token = root["deviceToken"]?.jsonPrimitive?.contentOrNull
        val id = root["deviceId"]?.jsonPrimitive?.contentOrNull
        val children = childrenIn(root) ?: emptyList()
        val shorts = root["shorts"]?.jsonPrimitive?.contentOrNull == "block"
        if (token.isNullOrEmpty() || id.isNullOrEmpty()) null else PairResult.Paired(id, token, children, shorts)
    } catch (e: Exception) {
        null
    }

}
