package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.JsonPostResponse
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.VoteItem
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 投票帖 — reading a vote, casting one, and the owner/moderator operations.
 *
 * Nothing here is cached. A vote is per-account server state whose very *shape* changes once this
 * account has voted (before that the options carry no counts at all), so a stored copy would draw a
 * resultless panel for a reader who had just voted from the web. The post body caches only the
 * placeholder's id, which is what keeps a thread readable offline with the vote showing as unread.
 *
 * Every path here needs `x-dynamic-sign`; see
 * [io.github.nodyssey.core.net.DynamicSignInterceptor], which adds it to the whole `/api/vote/`
 * family so no caller has to remember.
 */
interface VoteRepository {
    suspend fun info(voteId: Long): Vote

    /** Casts a vote. Single-choice passes one id — the wire format is an array either way. */
    suspend fun submit(
        voteId: Long,
        itemIds: List<Long>,
    )

    /** @return the new vote's id, which goes into the body as `nsapp://vote?id=N`. */
    suspend fun create(
        title: String,
        multiple: Boolean,
        isPublic: Boolean,
        items: List<String>,
    ): Long

    /** Locking is the owner's; unlocking is a moderator's. The site enforces both. */
    suspend fun setLocked(
        voteId: Long,
        locked: Boolean,
    )

    /** Moderators only — anyone else gets `Not admin account`. */
    suspend fun delete(voteId: Long)

    /** One page of an option's voters as bare uids. Public votes only; ten to a page. */
    suspend fun voters(
        itemId: Long,
        page: Int,
    ): List<Long>
}

class NetworkVoteRepository(
    private val api: JsonApi,
) : VoteRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun info(voteId: Long): Vote {
        val root = parse(api.getJson(NodeSeekJsonClient.voteInfoPath(voteId)))
        requireSuccess(root)
        val vote = root["vote"]?.jsonObject ?: throw SiteException(SiteError.Unparsable)
        return Vote(
            id = vote.long("id") ?: voteId,
            title = vote.text("title").orEmpty(),
            ownerUid = vote.long("uid") ?: -1L,
            isPublic = vote.bool("isPublic") ?: false,
            locked = vote.bool("locked") ?: false,
            multiple = vote.bool("multiple") ?: false,
            items =
            vote["items"]?.jsonArray.orEmpty().mapNotNull { entry ->
                val item = entry as? JsonObject ?: return@mapNotNull null
                VoteItem(
                    itemId = item.long("vote_item_id") ?: return@mapNotNull null,
                    text = item.text("text").orEmpty(),
                    voted = item.bool("voted") ?: false,
                    // Absent until this account has voted — and absent is not zero. See [VoteItem].
                    count = item.int("count"),
                    voters = item["voters"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.longOrNull },
                )
            },
        )
    }

    override suspend fun submit(
        voteId: Long,
        itemIds: List<Long>,
    ) {
        write(
            method = METHOD_POST,
            path = NodeSeekJsonClient.PATH_VOTE_SUBMIT,
            body = """{"ids":[${itemIds.joinToString(",")}]}""",
        )
    }

    override suspend fun create(
        title: String,
        multiple: Boolean,
        isPublic: Boolean,
        items: List<String>,
    ): Long {
        val payload =
            buildString {
                append("""{"title":""")
                append(quote(title))
                append(""","multiple":$multiple,"isPublic":$isPublic,"items":[""")
                append(items.joinToString(",") { quote(it) })
                append("]}")
            }
        val root = write(METHOD_POST, NodeSeekJsonClient.PATH_VOTE_CREATE, payload)
        // The site's own client reads `vote.id` here and nothing else, so a missing one means the
        // answer is not the shape we know — inserting a marker pointing at nothing would be worse.
        return root["vote"]?.jsonObject?.long("id") ?: throw SiteException(SiteError.Unparsable)
    }

    override suspend fun setLocked(
        voteId: Long,
        locked: Boolean,
    ) {
        write(METHOD_POST, NodeSeekJsonClient.voteLockPath(voteId), """{"locked":$locked}""")
    }

    override suspend fun delete(voteId: Long) {
        // A DELETE with a body, which is unusual but is what the site sends and what it accepts.
        write(METHOD_DELETE, NodeSeekJsonClient.voteInfoPath(voteId), """{"deleted":true}""")
    }

    override suspend fun voters(
        itemId: Long,
        page: Int,
    ): List<Long> {
        val root = parse(api.getJson(NodeSeekJsonClient.voterOfItemPath(itemId, page)))
        requireSuccess(root)
        return root["voters"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.longOrNull }
    }

    /**
     * A write whose answer is read before its status code.
     *
     * The vote endpoints refuse with 403, 422 and 500 depending on what went wrong, each carrying the
     * sentence worth showing. Going through [JsonApi.postJson] would turn "You are not the owner of
     * vote" into "please sign in".
     */
    private suspend fun write(
        method: String,
        path: String,
        body: String,
    ): JsonObject {
        val response: JsonPostResponse = api.sendJson(method, path, body)
        val root =
            try {
                json.parseToJsonElement(response.body).jsonObject
            } catch (exception: IllegalArgumentException) {
                // No JSON at all: fall back to the status, which is then the only thing we know.
                if (!response.isSuccessful) throw SiteException(SiteError.Http(response.code), exception)
                throw SiteException(SiteError.Unparsable, exception)
            }
        requireSuccess(root)
        return root
    }

    private fun parse(payload: String): JsonObject =
        try {
            json.parseToJsonElement(payload).jsonObject
        } catch (exception: IllegalArgumentException) {
            throw SiteException(SiteError.Unparsable, exception)
        }

    private fun requireSuccess(root: JsonObject) {
        if (root.bool("success") == true) return
        throw SiteException(
            SiteError.Unknown,
            detail = root.text("message"),
        )
    }

    /** Minimal JSON string escaping; vote titles and options are free text typed by the user. */
    private fun quote(value: String): String = json.encodeToString(String.serializer(), value)

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

    private companion object {
        const val METHOD_POST = "POST"
        const val METHOD_DELETE = "DELETE"
    }
}
