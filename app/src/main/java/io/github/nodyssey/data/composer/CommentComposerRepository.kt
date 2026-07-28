package io.github.nodyssey.data.composer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.Selectors
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

private val Context.commentComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "comment-composer",
)

/**
 * An unsent reply.
 *
 * [quotedFloor]/[quotedText] survive the round trip so reopening the sheet restores the quote chip
 * along with the text — a reply written against a quote reads as a non sequitur without it.
 */
@Serializable
data class CommentDraft(
    val body: String = "",
    val quotedFloor: Int? = null,
    val quotedAuthor: String? = null,
    val quotedText: String? = null,
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
 * sentence is passed through as [NodeSeekException.detail] because it is more specific than anything
 * this layer could infer (it also carries the duplicate-reply and rate-limit refusals).
 */
class DefaultCommentComposerRepository(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
) : CommentComposerRepository {
    private val dataStore = context.applicationContext.commentComposerDataStore
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
        val postUrl = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(submission.postId)
        val request = Request.Builder()
            .url(
                NodeSeekSite.absoluteUrl(NodeSeekSite.NEW_COMMENT_API_PATH)
                    ?: error("Invalid comment path"),
            ).header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            // Presence is what the server checks — the site's own editor sends a fresh random value
            // per page load and never echoes one back from a cookie.
            .header("Csrf-Token", UUID.randomUUID().toString().replace("-", "").take(CSRF_TOKEN_LENGTH))
            .header("Origin", NodeSeekSite.BASE_URL)
            .header("Referer", postUrl)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (error: IOException) {
            throw NodeSeekException(NodeSeekError.Network, error)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            // Cloudflare answers a blocked write with 403 plus challenge HTML, so this runs before
            // the status check: "please verify" and "please sign in" are different recoveries.
            val isChallenge =
                it.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
                    Selectors.CLOUDFLARE_MARKERS.any(body::contains) ||
                    body.trimStart().startsWith("<")
            if (isChallenge) throw NodeSeekException(NodeSeekError.Cloudflare)
            if (it.code == 401 || it.code == 403) {
                throw NodeSeekException(NodeSeekError.LoginRequired)
            }
            if (!it.isSuccessful) {
                throw NodeSeekException(
                    error = NodeSeekError.Http(it.code),
                    detail = parseMessage(body),
                )
            }
            parsePublishResponse(body)
        }
    }

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
            .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            throw NodeSeekException(
                error = NodeSeekError.Unknown,
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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val CSRF_TOKEN_LENGTH = 16
        const val MAX_ERROR_DETAIL_LENGTH = 200
    }
}
