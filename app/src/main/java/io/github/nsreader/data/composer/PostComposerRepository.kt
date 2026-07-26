package io.github.nsreader.data.composer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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

@Serializable
enum class PostPermission(val wireValue: Int) {
    PUBLIC(0),
    LEVEL_ONE(1),
    PRIVATE(-1),
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

    suspend fun publish(submission: PostSubmission): Long
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

    override suspend fun publish(submission: PostSubmission): Long = withContext(dispatchers.io) {
        val payload = json.encodeToString(
            PublishPayload(
                title = submission.title.trim(),
                content = submission.body.trim(),
                category = submission.boardSlug,
                permission = submission.permission.wireValue,
                mode = "new-post",
            ),
        )
        val request = Request.Builder()
            .url(NodeSeekSite.absoluteUrl(PATH_NEW_POST) ?: error("Invalid publish path"))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("csrf-token", UUID.randomUUID().toString().replace("-", "").take(16))
            .header("Origin", NodeSeekSite.BASE_URL)
            .header("Referer", NodeSeekSite.BASE_URL + "/new-discussion")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (error: IOException) {
            throw NodeSeekException(NodeSeekError.Network, error)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (it.code == 401 || it.code == 403) {
                throw NodeSeekException(NodeSeekError.LoginRequired)
            }
            if (!it.isSuccessful) throw NodeSeekException(NodeSeekError.Http(it.code))
            if (body.trimStart().startsWith("<")) throw NodeSeekException(NodeSeekError.Cloudflare)
            parsePublishResponse(body, it.header("Location"))
        }
    }

    @Serializable
    private data class PublishPayload(
        val title: String,
        val content: String,
        val category: String,
        val permission: Int,
        val mode: String,
    )

    private fun parsePublishResponse(body: String, location: String?): Long {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = root["message"]?.jsonPrimitive?.contentOrNull
        if (!success) error(message ?: "发布失败")

        fun idIn(name: String): Long? = root[name]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
        val nestedId = listOf("data", "detail").firstNotNullOfOrNull { key ->
            root[key]?.runCatching { jsonObject }?.getOrNull()?.let { nested ->
                listOf("postId", "id").firstNotNullOfOrNull { name ->
                    nested[name]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
                }
            }
        }
        return idIn("postId")
            ?: idIn("id")
            ?: nestedId
            ?: POST_ID.find(location.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
            ?: error("发布成功但没有返回帖子编号")
    }

    private companion object {
        const val PATH_NEW_POST = "/api/content/new-post"
        val DRAFT_KEY = stringPreferencesKey("draft")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val POST_ID = Regex("""/post-(\d+)""")
    }
}
