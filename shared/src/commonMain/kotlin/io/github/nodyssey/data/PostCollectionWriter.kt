package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the site said about a collection toggle that went through. */
data class CollectionOutcome(
    /** Where the thread ended up, per the site's own echo — not per what we asked for. */
    val collected: Boolean,
    /** How many accounts collect this thread now, when the answer carried it. */
    val postCollectionCount: Int?,
    /** How many threads this account collects now; the profile header shows it. */
    val userCollectionCount: Int?,
)

/**
 * Collects and un-collects a thread, and reads the site's verdict.
 *
 * Pure transport, kept apart from [PostRepository] for the same reason [PostReactionWriter] is: the
 * caller owns the thread and does the Room write. This class never touches the database.
 *
 * Unlike the three reactions this one is reversible, so a refusal is worth showing verbatim rather
 * than treating as a spent action — hence the site's own sentence in [SiteException.detail].
 */
class PostCollectionWriter(
    private val api: JsonApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun setCollected(
        postId: Long,
        collected: Boolean,
    ): CollectionOutcome {
        val answer =
            api.postJson(
                path = NodeSeekJsonClient.PATH_COLLECTION,
                body = """{"postId":$postId,"action":"${if (collected) ACTION_ADD else ACTION_REMOVE}"}""",
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId),
            )

        val root =
            try {
                json.parseToJsonElement(answer).jsonObject
            } catch (exception: IllegalArgumentException) {
                throw SiteException(SiteError.Unparsable, exception)
            }

        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
            throw SiteException(
                SiteError.Unknown,
                detail = root["message"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            )
        }

        return CollectionOutcome(
            /*
             * The echo wins over what we sent. Tapping twice in quick succession, or tapping a star
             * whose stored state had gone stale, both end with the site and the app disagreeing about
             * which way the toggle went — and only one of them knows.
             */
            collected =
            when (root["message"]?.jsonPrimitive?.contentOrNull) {
                MESSAGE_ADDED -> true
                MESSAGE_REMOVED -> false
                else -> collected
            },
            postCollectionCount = root.int("postCollectionCount"),
            userCollectionCount = root.int("userCollectionCount"),
        )
    }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private companion object {
        const val ACTION_ADD = "add"
        const val ACTION_REMOVE = "remove"
        const val MESSAGE_ADDED = "added"
        const val MESSAGE_REMOVED = "removed"
    }
}
