package io.github.nodyssey.data.composer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.Selectors
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An unsent reply.
 *
 * [replyToFloor]/[replyToText] survive the round trip so reopening the sheet restores the 回复 chip
 * along with the text — a reply written at somebody reads as a non sequitur without it. Quotes need
 * no fields of their own: 引用 writes Markdown straight into [body], so they are saved with it.
 */
@Serializable
data class CommentDraft(
    val body: String = "",
    val replyToFloor: Int? = null,
    val replyToAuthor: String? = null,
    val replyToText: String? = null,
    val savedAtMillis: Long = 0L,
) {
    val hasContent: Boolean get() = body.isNotBlank()
}

data class CommentSubmission(
    val postId: Long,
    val body: String,
    val quotedFloor: Int? = null,
)

interface CommentComposerRepository {
    fun draft(postId: Long): Flow<CommentDraft?>

    suspend fun saveDraft(postId: Long, draft: CommentDraft)

    suspend fun deleteDraft(postId: Long)

    /** @return the new floor number when the site reports one. */
    suspend fun publish(submission: CommentSubmission): Int?

    /**
     * Rewrites a reply this account wrote. Addressed by `commentId` rather than by floor, because
     * that is what the site's endpoint takes and floors renumber when one above is removed.
     *
     * [postId] is only the `Referer`; the endpoint does not read it. Defaulted so the read-only and
     * publish-only test doubles stay small.
     */
    suspend fun edit(postId: Long, commentId: Long, body: String): Unit =
        throw UnsupportedOperationException("This repository does not edit comments")
}

/**
 * Drafts local, publishing live against `/api/content/new-comment`.
 *
 * The endpoint takes the same shape as its `new-discussion` sibling — a JSON body with a `mode`
 * discriminator and a `csrf-token` header the server only checks for presence — and answers
 * `{"success":true,"redirect":…,"redirectHash":"#3"}`. The floor number lives *only* in that hash,
 * so a missing or unreadable hash yields null rather than a guess: the caller treats null as "posted,
 * floor unknown" and scrolls to the bottom, which is right, whereas a wrong number would scroll to
 * somebody else's reply.
 *
 * Rejections arrive as HTTP 400 with `{"success":false,"message":"内容不能为空"}`; the site's own
 * sentence is passed through as [SiteException.detail] because it is more specific than anything
 * this layer could infer (it also carries the duplicate-reply and rate-limit refusals).
 */
class DefaultCommentComposerRepository(
    private val dataStore: DataStore<Preferences>,
    private val transport: HttpTransport,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
    /** Keeps the feed from announcing this account's own accepted reply as new. */
    private val onReplyPublished: suspend (Long) -> Unit = {},
) : CommentComposerRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun draft(postId: Long): Flow<CommentDraft?> = dataStore.data.map { preferences ->
        preferences[key(postId)]?.let { encoded ->
            runCatching { json.decodeFromString<CommentDraft>(encoded) }.getOrNull()
        }
    }

    override suspend fun saveDraft(postId: Long, draft: CommentDraft) {
        dataStore.edit { preferences ->
            preferences[key(postId)] = json.encodeToString(draft.copy(savedAtMillis = clock.nowMillis()))
        }
    }

    override suspend fun deleteDraft(postId: Long) {
        dataStore.edit { preferences -> preferences.remove(key(postId)) }
    }

    override suspend fun publish(submission: CommentSubmission): Int? = withContext(dispatchers.io) {
        val payload = json.encodeToString(
            CommentPayload(
                content = submission.body.trim(),
                mode = NodeSeekSite.NEW_COMMENT_MODE,
                postId = submission.postId,
            ),
        )
        val response = transport.execute(requestTo(NodeSeekSite.NEW_COMMENT_API_PATH, submission.postId, payload))
        throwIfChallenge(response.header("cf-mitigated"), response.body)
        if (response.code == 401 || response.code == 403) {
            throw SiteException(SiteError.LoginRequired)
        }
        if (!response.isSuccessful) {
            throw SiteException(
                error = SiteError.Http(response.code),
                detail = parseMessage(response.body),
            )
        }
        val floor = parsePublishResponse(response.body)
        // The server has already accepted the reply. Local bookkeeping must not turn that into
        // a reported publish failure and invite a duplicate retry if Room ever refuses a write.
        runCatchingExceptCancellation { onReplyPublished(submission.postId) }
        floor
    }

    override suspend fun edit(postId: Long, commentId: Long, body: String): Unit = withContext(dispatchers.io) {
        val payload = json.encodeToString(
            EditPayload(
                content = body.trim(),
                mode = NodeSeekSite.EDIT_COMMENT_MODE,
                commentId = commentId,
            ),
        )
        val response = transport.execute(requestTo(NodeSeekSite.EDIT_COMMENT_API_PATH, postId, payload))
        throwIfChallenge(response.header("cf-mitigated"), response.body)
        if (response.code == 401 || response.code == 403) {
            throw SiteException(SiteError.LoginRequired)
        }
        if (!response.isSuccessful) {
            throw SiteException(error = SiteError.Http(response.code), detail = parseMessage(response.body))
        }
        // Same reason [parsePublishResponse] re-reads a 200: a refusal the board makes rather
        // than the router arrives as `success:false` with the sentence to show.
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }
            .getOrElse { throw SiteException(SiteError.Unparsable, it) }
        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
            throw SiteException(
                error = SiteError.Unknown,
                detail = root["message"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    /**
     * The request both writes send — same headers, same origin, only the path and the body differ.
     *
     * `Csrf-Token` is present rather than correct: the server checks that a value arrived, and the
     * site's own editor sends a fresh random one per page load without ever echoing a cookie back.
     */
    private fun requestTo(path: String, postId: Long, payload: String): HttpRequest =
        HttpRequest(
            url = NodeSeekSite.absoluteUrl(path) ?: error("Invalid comment path"),
            method = "POST",
            headers =
            mapOf(
                "Accept" to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest",
                "Csrf-Token" to Uuid.random().toString().replace("-", "").take(CSRF_TOKEN_LENGTH),
                "Origin" to NodeSeekSite.BASE_URL,
                "Referer" to NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId),
            ),
            body = HttpBody.Text(payload),
        )

    /**
     * Cloudflare answers a blocked write with 403 plus challenge HTML, so this runs before any
     * status handling: "please verify" and "please sign in" are different recoveries.
     */
    private fun throwIfChallenge(cfMitigated: String?, body: String) {
        val isChallenge =
            cfMitigated?.equals("challenge", ignoreCase = true) == true ||
                Selectors.CLOUDFLARE_MARKERS.any(body::contains) ||
                body.trimStart().startsWith("<")
        if (isChallenge) throw SiteException(SiteError.Cloudflare)
    }

    @Serializable
    private data class EditPayload(
        val content: String,
        val mode: String,
        val commentId: Long,
    )

    @Serializable
    private data class CommentPayload(
        val content: String,
        val mode: String,
        val postId: Long,
    )

    /**
     * A 200 is not yet a success: the endpoint also answers 200 with `success:false` when the
     * content was accepted by the router but refused by the board (a locked thread, for one).
     */
    private fun parsePublishResponse(body: String): Int? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw SiteException(SiteError.Unparsable, it) }
        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            throw SiteException(
                error = SiteError.Unknown,
                detail = root["message"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return NodeSeekSite.parseFloorHash(root["redirectHash"]?.jsonPrimitive?.contentOrNull)
    }

    private fun parseMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("<")) return null
        return runCatching {
            json.parseToJsonElement(trimmed).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: trimmed.take(MAX_ERROR_DETAIL_LENGTH)
    }

    private fun key(postId: Long) = stringPreferencesKey("draft-$postId")

    private companion object {
        const val CSRF_TOKEN_LENGTH = 16
        const val MAX_ERROR_DETAIL_LENGTH = 200
    }
}
