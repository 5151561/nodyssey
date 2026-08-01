package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the site said about one reaction that went through. */
data class ReactionOutcome(
    /** The new value of the tally that was spent on — the site sends only the one it changed. */
    val current: Int,
    /** Chicken legs left afterwards, when the answer carried them. */
    val coin: Int?,
)

/**
 * Sends the three one-way marks and reads the site's own verdict.
 *
 * Kept apart from [PostRepository] because it is pure transport: the caller owns the thread and is
 * the one that writes the new tally through Room. This class never touches the database.
 *
 * A `success:false` body is a *refusal*, not a fault — "已经进行过加鸡腿操作", "鸡腿不足" — so it
 * throws with the site's sentence in [NodeSeekException.detail] and lets the screen show it verbatim
 * rather than mapping it onto a status code the user would have to interpret.
 */
class PostReactionWriter(
    private val api: JsonApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun react(
        postId: Long,
        commentId: Long,
        action: ReactionAction,
    ): ReactionOutcome {
        val answer =
            api.postJson(
                path = NodeSeekSite.reactionApiPath(action.apiAction),
                body = """{"commentId":$commentId,"action":"add"}""",
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId),
            )

        val root =
            try {
                json.parseToJsonElement(answer).jsonObject
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            }

        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
            throw NodeSeekException(
                NodeSeekError.Unknown,
                detail = root["message"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            )
        }

        return ReactionOutcome(
            // Absent `current` is not an error: the mark landed, and the tally is one higher than
            // whatever we were showing. Signalling failure here would tell the user to spend again.
            current = root.int("current") ?: -1,
            coin = root.int("coin"),
        )
    }

    /**
     * Today's free 加鸡腿 allowance, or null when the site would not say.
     *
     * Only ever used to soften a confirmation ("本次投喂免费"), so every failure is null: a reader
     * about to spend a chicken leg should not be stopped by a quota lookup failing.
     */
    suspend fun freeChickenLegs(): FreeChickenLegs? =
        try {
            val root = json.parseToJsonElement(api.getJson(NodeSeekSite.FREE_LIKE_QUOTA_API_PATH)).jsonObject
            val max = root.int("maxFreeLike")
            val used = root.int("freeLikeUsed")
            if (max == null || used == null) null else FreeChickenLegs(max = max, used = used)
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: NodeSeekException) {
            null
        }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
}

/** How many 加鸡腿 today's free allowance still covers. */
data class FreeChickenLegs(
    val max: Int,
    val used: Int,
) {
    val remaining: Int get() = (max - used).coerceAtLeast(0)
}

/** Folds a landed reaction into the tallies already on screen. */
fun PostReactions?.applying(
    action: ReactionAction,
    outcome: ReactionOutcome,
): PostReactions {
    val base = this ?: PostReactions()

    // `current` is authoritative when the site sent it; otherwise step the tally we were showing.
    fun tally(previous: Int): Int = if (outcome.current >= 0) outcome.current else previous + 1
    return when (action) {
        ReactionAction.Upvote -> base.copy(upvoteCount = tally(base.upvoteCount), upvoted = true)
        ReactionAction.ChickenLeg -> base.copy(likeCount = tally(base.likeCount), liked = true)
        ReactionAction.Dislike -> base.copy(dislikeCount = tally(base.dislikeCount), disliked = true)
    }
}
