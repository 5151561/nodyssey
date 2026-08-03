package io.github.nodyssey.core.html

import io.github.nodyssey.model.PostReactions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jsoup.nodes.Document

/**
 * What the post page's own `__config__` blob knows about each floor, keyed by `commentId`.
 *
 * [reactions] is empty and [blockedCommentIds] is empty when the page carried no blob — which is a
 * different claim from "no marks and nothing blocked", and why both are read in one pass.
 */
internal data class PostConfig(
    val reactions: Map<Long, PostReactions> = emptyMap(),
    val blockedCommentIds: Set<Long> = emptySet(),
)

/**
 * Per-floor state, read out of the post page's own `__config__` blob rather than off the markup.
 *
 * The rendered floor shows the three counts, but not whether *this* account has already spent a
 * chicken leg on it — the site's client keeps that in [SiteBootstrap]'s payload and greys its own
 * buttons from there. Scraping the visible numbers would therefore get us two thirds of the state and
 * leave the third to be discovered by having a write rejected. `blocked` is in the same payload and
 * is likewise the server's answer, not ours.
 *
 * Every failure here is silent and yields no entry. A thread whose blob is missing or reshaped still
 * has an article worth reading, and [PostDetailParser] must not lose it over a tally.
 */
internal object PostConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** The blob's per-floor state, empty when the page carried none. The body is a comment here too. */
    fun parse(document: Document): PostConfig {
        val decoded = SiteBootstrap.decodeOrNull(document) ?: return PostConfig()
        val comments =
            try {
                json
                    .parseToJsonElement(decoded)
                    .jsonObject["postData"]
                    ?.jsonObject
                    ?.get("comments")
                    ?.jsonArray
            } catch (exception: IllegalArgumentException) {
                null
            } ?: return PostConfig()

        val reactions = mutableMapOf<Long, PostReactions>()
        val blocked = mutableSetOf<Long>()
        comments.forEach { entry ->
            val comment = entry as? JsonObject ?: return@forEach
            val commentId = comment.long("commentId") ?: return@forEach
            reactions[commentId] =
                PostReactions(
                    likeCount = comment.int("likeCount"),
                    dislikeCount = comment.int("dislikeCount"),
                    upvoteCount = comment.int("upvoteCount"),
                    liked = comment.bool("liked"),
                    disliked = comment.bool("disliked"),
                    upvoted = comment.bool("upvoted"),
                )
            if (comment.bool("blocked")) blocked += commentId
        }
        return PostConfig(reactions = reactions, blockedCommentIds = blocked)
    }

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    /*
     * Via the string form, not `intOrNull`: the counts have arrived as both `12` and `"12"` depending
     * on the field, and a tally that silently reads zero is worse than one that reads slightly late.
     */
    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false
}
