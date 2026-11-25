package dev.tvlimiter.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class RequestStatus { PENDING, APPROVED, DENIED }

/** A child's request, from the block screen, for a parent to approve the video. */
data class ApprovalRequest(val id: String, val status: RequestStatus)

/** What made a video over a limit. Only the TV turns this into words, so a child never sees a parent's text. */
enum class OverKind { VALUE, WORLDVIEW, VIDEO_BAN, CHANNEL_BAN, LEARNED_MATCH, AGE_RESTRICTED, OTHER }

/**
 * What TubeGuard says about one watch. [decided] is false while the video is still being identified or checked.
 * Only an "over" outcome blocks: "near" stays an alert for the parents.
 */
data class WatchOutcome(
    val decided: Boolean,
    val outcome: String?,
    val kind: OverKind?,
    /** A category key such as "scary", or a topic key such as "politics". */
    val value: String?,
    /** How much of the category the video has: "mild", "some" or "a_lot". */
    val level: String?,
    val request: ApprovalRequest?,
) {
    val isOver: Boolean get() = decided && outcome == "over"
}

/** What asking TubeGuard about a watch came to. */
sealed interface Fetch {
    data class Got(val outcome: WatchOutcome) : Fetch

    /** The backend doesn't know this watch (it is too old, or it is another TV's). */
    data object Gone : Fetch

    /** No answer. The TV never blocks on a failure to ask. */
    data object Unreachable : Fetch
}

/** Reads the outcome TubeGuard sends in a report reply or for `GET /api/device/watches/:id`. */
object OutcomeWire {
    /** The outcome in [body], or null when the reply carries none (it is not known yet) or can't be read. */
    fun parse(body: String): WatchOutcome? = try {
        val outcome = Json.parseToJsonElement(body).jsonObject["outcome"] as? JsonObject
        outcome?.let(::fromJson)
    } catch (e: Exception) {
        null
    }

    private fun fromJson(o: JsonObject): WatchOutcome {
        fun text(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
        val request = (o["request"] as? JsonObject)?.let { r ->
            val id = (r["id"] as? JsonPrimitive)?.contentOrNull
            val status = when ((r["status"] as? JsonPrimitive)?.contentOrNull) {
                "pending" -> RequestStatus.PENDING
                "approved" -> RequestStatus.APPROVED
                "denied" -> RequestStatus.DENIED
                else -> null
            }
            if (id != null && status != null) ApprovalRequest(id, status) else null
        }
        val decided = (o["decided"] as? JsonPrimitive)?.contentOrNull == "true"
        return WatchOutcome(
            decided = decided,
            outcome = text("outcome"),
            kind = when (text("kind")) {
                null -> null
                "value" -> OverKind.VALUE
                "worldview" -> OverKind.WORLDVIEW
                "video_ban" -> OverKind.VIDEO_BAN
                "channel_ban" -> OverKind.CHANNEL_BAN
                "learned_match" -> OverKind.LEARNED_MATCH
                "age_restricted" -> OverKind.AGE_RESTRICTED
                else -> OverKind.OTHER
            },
            value = text("value"),
            level = text("level"),
            request = request,
        )
    }

    /** The id of the approval request in a `request-approval` reply, and its status. */
    fun parseRequest(body: String): ApprovalRequest? = try {
        val root = Json.parseToJsonElement(body).jsonObject
        val id = root["requestId"]?.jsonPrimitive?.contentOrNull
        val status = when (root["status"]?.jsonPrimitive?.contentOrNull) {
            "approved" -> RequestStatus.APPROVED
            "denied" -> RequestStatus.DENIED
            else -> RequestStatus.PENDING
        }
        if (id.isNullOrEmpty()) null else ApprovalRequest(id, status)
    } catch (e: Exception) {
        null
    }
}
