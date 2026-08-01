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
 * Reaction tallies, read out of the post page's own `__config__` blob rather than off the markup.
 *
 * The rendered floor shows the three counts, but not whether *this* account has already spent a
 * chicken leg on it — the site's client keeps that in [SiteBootstrap]'s payload and greys its own
 * buttons from there. Scraping the visible numbers would therefore get us two thirds of the state and
 * leave the third to be discovered by having a write rejected.
 *
 * Every failure here is silent and yields no entry. A thread whose blob is missing or reshaped still
 * has an article worth reading, and [PostDetailParser] must not lose it over a tally.
 */
internal object PostConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Reactions by `commentId`, empty when the page carried none. The body is a comment here too. */
    fun parseReactions(document: Document): Map<Long, PostReactions> {
        val decoded = SiteBootstrap.decodeOrNull(document) ?: return emptyMap()
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
            } ?: return emptyMap()

        return comments.mapNotNull { entry ->
            val comment = entry as? JsonObject ?: return@mapNotNull null
            val commentId = comment.long("commentId") ?: return@mapNotNull null
            commentId to
                PostReactions(
                    likeCount = comment.int("likeCount"),
                    dislikeCount = comment.int("dislikeCount"),
                    upvoteCount = comment.int("upvoteCount"),
                    liked = comment.bool("liked"),
                    disliked = comment.bool("disliked"),
                    upvoted = comment.bool("upvoted"),
                )
        }.toMap()
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
