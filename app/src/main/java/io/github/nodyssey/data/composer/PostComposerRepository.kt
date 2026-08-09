package io.github.nodyssey.data.composer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.PostListParser
import io.github.nodyssey.core.html.Selectors
import io.github.nodyssey.model.FeedSort
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

private val Context.postComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "post-composer",
)

/** User-authored draft and publish contract for the native post editor. */
@Serializable
data class PostDraft(
    val title: String = "",
    val body: String = "",
    val boardSlug: String? = null,
    val boardTitle: String? = null,
    val permission: PostPermission = PostPermission.PUBLIC,
    val savedAtMillis: Long = 0L,
)

/**
 * 阅读权限, as the site models it: one number, the level a reader needs. `0` lets anyone in, `255`
 * is the author alone, and everything between is a level floor — the same `rank` the ruling board
 * reports when a moderator changes a thread's 阅读权限.
 *
 * Not a closed set, which is why this is a number and not an enum: an account may hold a thread to
 * any level up to its own, so the choices depend on who is posting. See [options].
 */
@Serializable(with = PostPermissionSerializer::class)
data class PostPermission(val wireValue: Int) {
    /** The level a reader needs, or null at either end of the scale (公开 and 私有). */
    val requiredLevel: Int? get() = wireValue.takeIf { it in 1..<PRIVATE_WIRE_VALUE }

    companion object {
        private const val PRIVATE_WIRE_VALUE = 255

        val PUBLIC = PostPermission(0)
        val PRIVATE = PostPermission(PRIVATE_WIRE_VALUE)

        /**
         * 公开, then Lv1 up to the author's own level, then 私有.
         *
         * A null [selfRank] means the profile has not arrived yet — the account endpoint is a
         * round-trip away and can be refused — so Lv1 is offered on its own, which is exactly what
         * the menu held back when the choice was hard-coded. A known rank of 0 offers no level at
         * all, because there is none to require.
         */
        fun options(selfRank: Int?): List<PostPermission> =
            buildList {
                add(PUBLIC)
                (1..(selfRank ?: 1)).forEach { level -> add(PostPermission(level)) }
                add(PRIVATE)
            }
    }
}

/**
 * Ints on the wire and in new drafts. Drafts saved before 阅读权限 became a range hold the old enum
 * names instead, and a decode failure there costs the user the entire draft, not just the choice.
 */
internal object PostPermissionSerializer : KSerializer<PostPermission> {
    override val descriptor = PrimitiveSerialDescriptor("io.github.nodyssey.PostPermission", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: PostPermission) = encoder.encodeInt(value.wireValue)

    override fun deserialize(decoder: Decoder): PostPermission {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive
            ?: return PostPermission(decoder.decodeInt())
        primitive.intOrNull?.let { return PostPermission(it) }
        return when (primitive.contentOrNull) {
            "LEVEL_ONE" -> PostPermission(1)
            "PRIVATE" -> PostPermission.PRIVATE
            else -> PostPermission.PUBLIC
        }
    }
}

data class PostSubmission(
    val title: String,
    val body: String,
    val boardSlug: String,
    val permission: PostPermission,
)

interface PostComposerRepository {
    val draft: Flow<PostDraft?>

    suspend fun saveDraft(draft: PostDraft)

    suspend fun deleteDraft()

    suspend fun publish(submission: PostSubmission): Long?
}

class DefaultPostComposerRepository(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
) : PostComposerRepository {
    private val dataStore = context.applicationContext.postComposerDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override val draft: Flow<PostDraft?> = dataStore.data.map { preferences ->
        preferences[DRAFT_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<PostDraft>(encoded) }.getOrNull()
        }
    }

    override suspend fun saveDraft(draft: PostDraft) {
        dataStore.edit { preferences ->
            preferences[DRAFT_KEY] = json.encodeToString(draft.copy(savedAtMillis = clock.nowMillis()))
        }
    }

    override suspend fun deleteDraft() {
        dataStore.edit { preferences -> preferences.remove(DRAFT_KEY) }
    }

    override suspend fun publish(submission: PostSubmission): Long? = withContext(dispatchers.io) {
        val payload = json.encodeToString(
            PublishPayload(
                title = submission.title.trim(),
                content = submission.body.trim(),
                category = submission.boardSlug,
                rank = submission.permission.wireValue,
                mode = "new-discussion",
            ),
        )
        val request = Request.Builder()
            .url(NodeSeekSite.absoluteUrl(NodeSeekSite.NEW_DISCUSSION_API_PATH) ?: error("Invalid publish path"))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Csrf-Token", UUID.randomUUID().toString().replace("-", "").take(16))
            .header("Origin", NodeSeekSite.BASE_URL)
            .header("Referer", NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (error: IOException) {
            throw SiteException(SiteError.Network, error)
        }
        response.use {
            val body = it.body.string()
            // Cloudflare answers a blocked request with 403 plus challenge HTML, so the challenge
            // check must run before the status check — "please verify" and "please sign in" send the
            // user down entirely different recovery paths.
            val isChallenge =
                it.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
                    Selectors.CLOUDFLARE_MARKERS.any(body::contains)
            if (isChallenge) throw SiteException(SiteError.Cloudflare)
            if (it.code == 401 || it.code == 403) {
                throw SiteException(SiteError.LoginRequired)
            }
            if (!it.isSuccessful) {
                throw SiteException(
                    error = SiteError.Http(it.code),
                    detail = parseErrorDetail(body),
                )
            }
            if (body.trimStart().startsWith("<")) throw SiteException(SiteError.Cloudflare)
            parsePublishResponse(body, it.header("Location")) ?: findPublishedPostId(submission)
        }
    }

    @Serializable
    private data class PublishPayload(
        val title: String,
        val content: String,
        val category: String,
        val rank: Int,
        val mode: String,
    )

    private fun parsePublishResponse(body: String, location: String?): Long? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw SiteException(SiteError.Unparsable, it) }
        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = root["message"]?.jsonPrimitive?.contentOrNull
        if (!success) error(message ?: "发布失败")

        fun idIn(name: String): Long? = root[name]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
        val nestedId = listOf("data", "detail").firstNotNullOfOrNull { key ->
            root[key]?.runCatching { jsonObject }?.getOrNull()?.let { nested ->
                listOf("postId", "post_id", "id").firstNotNullOfOrNull { name ->
                    nested[name]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
                }
            }
        }
        return idIn("postId")
            ?: idIn("post_id")
            ?: idIn("id")
            ?: nestedId
            ?: POST_ID.find(location.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * The current endpoint's success response contains no topic id. Resolve the new thread from
     * the board's post-time feed; failure here must not turn a successful publish into a retry
     * prompt, otherwise the next tap creates a duplicate topic.
     */
    private fun findPublishedPostId(submission: PostSubmission): Long? {
        val path = NodeSeekSite.listPath(submission.boardSlug, page = 1, sort = FeedSort.POST_TIME)
        val request = Request.Builder()
            .url(NodeSeekSite.absoluteUrl(path) ?: return null)
            .header("Accept", NodeSeekSite.HTML_ACCEPT)
            .header("Referer", NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH)
            .get()
            .build()
        val response =
            runCatchingExceptCancellation { okHttpClient.newCall(request).execute() }
                .getOrNull()
                ?: return null
        return response.use {
            if (!it.isSuccessful) return@use null
            val html = it.body.string()
            runCatching {
                PostListParser.parse(html, page = 1).posts.firstOrNull { post ->
                    post.title == submission.title.trim() && post.categorySlug == submission.boardSlug
                }?.postId
            }.getOrNull()
        }
    }

    private fun parseErrorDetail(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("<")) return null
        return runCatching {
            val root = json.parseToJsonElement(trimmed).jsonObject
            listOf("message", "error").firstNotNullOfOrNull { key ->
                root[key]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull() ?: trimmed.take(MAX_ERROR_DETAIL_LENGTH)
    }

    private companion object {
        val DRAFT_KEY = stringPreferencesKey("draft")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val POST_ID = Regex("""/post-(\d+)""")
        const val MAX_ERROR_DETAIL_LENGTH = 200
    }
}
