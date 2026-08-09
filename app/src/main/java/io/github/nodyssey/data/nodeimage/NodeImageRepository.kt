package io.github.nodyssey.data.nodeimage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeImageSite
import io.github.nodyssey.core.html.Selectors
import io.github.plaza.designsys.theme.Sizes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

private val Context.nodeImageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nodeimage",
)

/** An image, ready to go out: already decoded, resized and re-encoded by the caller. */
data class NodeImageUpload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
) {
    // Data classes with an array member need these, or two uploads of the same photo compare unequal.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is NodeImageUpload &&
                    bytes.contentEquals(other.bytes) &&
                    fileName == other.fileName &&
                    mimeType == other.mimeType
                )

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + fileName.hashCode()) * 31 + mimeType.hashCode()
}

/** What the host answers an upload with. [url] is the CDN link that goes into the Markdown. */
data class NodeImageUploaded(
    val imageId: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
)

/** One row of 图床管理. [uploadTime] is the host's own string; the app does not re-interpret it. */
data class NodeImageItem(
    val imageId: String,
    val fileName: String,
    val url: String,
    val uploadTime: String?,
    val sizeBytes: Long,
    val mimeType: String?,
)

/**
 * Why a NodeImage call could not be completed.
 *
 * Separate from `NodeSeekError` on purpose: the recoveries do not overlap. A NodeSeek 401 means
 * "sign in to the forum", a NodeImage 401 means "your API key is wrong" — sending the user to the
 * forum's login page for that would be actively unhelpful.
 */
sealed interface NodeImageError {
    /** No API key stored yet. The only fix is the 图床 entry in 账号设置. */
    data object NotConfigured : NodeImageError

    /** The key was rejected. It was revoked, regenerated, or pasted wrong. */
    data object InvalidKey : NodeImageError

    /**
     * The endpoint wants a NodeSeek-authorized browser session; an API key is not enough.
     *
     * Measured, not assumed: on device, `GET /api/images` with a key that had *just* succeeded on
     * `POST /api/upload` answered 401 `{"error":"未认证，请先通过NodeSeek授权登录"}` (2026-07-28).
     * The host's API page documents the key for all four endpoints; only upload actually honours it.
     * Kept apart from [InvalidKey] because the key is fine, and telling the user to regenerate a
     * working key would break the half that does work.
     */
    data object SessionRequired : NodeImageError

    /** The host refused this particular file — too large, or a format it does not take. */
    data class Rejected(val statusCode: Int) : NodeImageError

    /**
     * Cloudflare answered instead of the host.
     *
     * `api.nodeimage.com` sits behind a managed challenge (confirmed 2026-07-28: a plain `curl` to
     * it gets the "Just a moment…" interstitial, not JSON). A phone's real UA normally passes, but
     * when it does not the recovery is *not* "check your API key" — the key was never looked at.
     * Told apart because the two failures send the user to completely different places.
     */
    data object Cloudflare : NodeImageError

    data class Http(val statusCode: Int) : NodeImageError

    data object Network : NodeImageError

    data object Unparsable : NodeImageError
}

class NodeImageException(
    val error: NodeImageError,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: error.toString(), cause)

/**
 * The app's side of nodeimage.com.
 *
 * Holds the API key and speaks the four endpoints the host documents. The key is the user's own
 * credential and is never sent anywhere but [NodeImageSite.API_BASE_URL] — which is why this class
 * takes its own [OkHttpClient] rather than the app's shared one: that client carries the NodeSeek
 * cookie jar and forces a `Referer: nodeseek.com` on every request, neither of which belongs on a
 * call to a third-party host holding a bearer-equivalent secret.
 */
interface NodeImageRepository {
    /** Null until the user has pasted one. Emits again when it changes, so screens stay in sync. */
    val apiKey: Flow<String?>

    suspend fun setApiKey(key: String)

    suspend fun clearApiKey()

    /** @param onProgress 0f–1f as bytes go out, for the tray's progress ring. */
    suspend fun upload(
        upload: NodeImageUpload,
        onProgress: (Float) -> Unit = {},
    ): NodeImageUploaded

    suspend fun images(): List<NodeImageItem>

    suspend fun delete(imageId: String)
}

class DefaultNodeImageRepository(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : NodeImageRepository {
    private val dataStore = context.applicationContext.nodeImageDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override val apiKey: Flow<String?> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences -> preferences[KEY_API_KEY]?.trim()?.ifBlank { null } }

    override suspend fun setApiKey(key: String) {
        dataStore.edit { preferences -> preferences[KEY_API_KEY] = key.trim() }
    }

    override suspend fun clearApiKey() {
        dataStore.edit { preferences -> preferences.remove(KEY_API_KEY) }
    }

    override suspend fun upload(
        upload: NodeImageUpload,
        onProgress: (Float) -> Unit,
    ): NodeImageUploaded = withContext(dispatchers.io) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                NodeImageSite.UPLOAD_FILE_FIELD,
                upload.fileName,
                ProgressRequestBody(upload.bytes, upload.mimeType.toMediaType(), onProgress),
            ).build()

        val payload = execute(
            request(NodeImageSite.UPLOAD_PATH).post(body).build(),
            keyIsEnough = true,
        )
        val root = payload.jsonObjectOrThrow()
        // The host answers 200 with `success:false` for a rejection it can describe, so a status
        // check alone would report a failed upload as a successful one with an empty URL.
        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
            throw NodeImageException(
                error = NodeImageError.Rejected(200),
                detail = root["message"]?.jsonPrimitive?.contentOrNull,
            )
        }
        /*
         * Two answer shapes, and the app has to read both.
         *
         * The key-authenticated endpoint this class uses answers snake_case with the URL nested:
         *   {"success":true,"image_id":"2ML…","filename":"2ML….webp","size":5316,
         *    "links":{"direct":"https://cdn.nodeimage.com/i/2ML….webp","html":…,"markdown":…}}
         * The site's own uploader — cookie-authenticated, `POST /upload` — answers camelCase and
         * flat: {"imageId":…,"url":"https://cdn…"}. Both were observed on 2026-07-28 (the second in
         * the browser, the first on device), so neither is hypothetical, and reading only the flat
         * one is exactly the bug that made every upload fail with "Unparsable" while the host had
         * already stored the image.
         */
        val url = root.stringAt("links", "direct")
            ?: root["url"]?.jsonPrimitive?.contentOrNull
            ?: throw NodeImageException(NodeImageError.Unparsable, detail = payload.take(200))
        NodeImageUploaded(
            imageId = (root["image_id"] ?: root["imageId"])?.jsonPrimitive?.contentOrNull.orEmpty(),
            fileName = root["filename"]?.jsonPrimitive?.contentOrNull ?: upload.fileName,
            url = url,
            sizeBytes = root["size"]?.jsonPrimitive?.longOrNull ?: upload.bytes.size.toLong(),
        )
    }

    private fun JsonObject.stringAt(vararg path: String): String? {
        var current: JsonElement = this
        for (segment in path) {
            current = runCatching { current.jsonObject[segment] }.getOrNull() ?: return null
        }
        return runCatching { current.jsonPrimitive.contentOrNull }.getOrNull()
    }

    override suspend fun images(): List<NodeImageItem> = withContext(dispatchers.io) {
        val payload = execute(request(NodeImageSite.IMAGES_PATH).get().build(), keyIsEnough = false)
        val element = runCatching { json.parseToJsonElement(payload) }
            .getOrElse { throw NodeImageException(NodeImageError.Unparsable, cause = it) }
        // A bare array today, but a paged `{images:[…]}` is the obvious next shape for this host, so
        // both are read rather than letting a server-side change empty the screen without a word.
        val rows = runCatching {
            element.jsonArray
        }.recoverCatching {
            element.jsonObject["images"]?.jsonArray ?: error("no images array")
        }.getOrElse { throw NodeImageException(NodeImageError.Unparsable, cause = it) }

        rows.mapNotNull { row ->
            val obj = runCatching { row.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["imageId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            NodeImageItem(
                imageId = id,
                fileName = obj["filename"]?.jsonPrimitive?.contentOrNull ?: id,
                url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                uploadTime = obj["uploadTime"]?.jsonPrimitive?.contentOrNull,
                // Sizes have arrived as JSON numbers so far; a stringly-typed one must not zero the row.
                sizeBytes = obj["size"]?.jsonPrimitive?.let {
                    it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.contentOrNull?.toLongOrNull()
                } ?: 0L,
                mimeType = obj["mimetype"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    override suspend fun delete(imageId: String) {
        withContext(dispatchers.io) {
            execute(request(NodeImageSite.imagePath(imageId)).delete().build(), keyIsEnough = false)
        }
    }

    private suspend fun request(path: String): Request.Builder {
        val key = apiKey.first() ?: throw NodeImageException(NodeImageError.NotConfigured)
        return Request.Builder()
            .url(NodeImageSite.absoluteApiUrl(path))
            .header("Accept", "application/json")
            .header(NodeImageSite.API_KEY_HEADER, key)
    }

    /**
     * @param keyIsEnough whether an API key alone authenticates this endpoint — true only for
     *   upload. It decides what a 401 *means*, which is the difference between "your key is broken"
     *   and "this one needs the website". See [NodeImageError.SessionRequired].
     * @return the response body, with every non-2xx already turned into a [NodeImageException].
     */
    private fun execute(request: Request, keyIsEnough: Boolean): String {
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (error: IOException) {
            throw NodeImageException(NodeImageError.Network, cause = error)
        }
        return response.use {
            val payload = it.body.string()
            // Runs first, and before the status check: Cloudflare wraps its interstitial in a 403,
            // which is the same status the host uses for a bad key. Reading the challenge as a bad
            // key would send the user off to regenerate a key that was never the problem.
            val isChallenge =
                it.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
                    Selectors.CLOUDFLARE_MARKERS.any(payload::contains) ||
                    payload.trimStart().startsWith("<")
            if (isChallenge) throw NodeImageException(NodeImageError.Cloudflare)

            val detail = runCatching {
                json.parseToJsonElement(payload).jsonObject.let { root ->
                    root["error"]?.jsonPrimitive?.contentOrNull
                        ?: root["message"]?.jsonPrimitive?.contentOrNull
                }
            }.getOrNull()
            when {
                it.isSuccessful -> payload

                it.code == 401 || it.code == 403 -> throw NodeImageException(
                    if (keyIsEnough) NodeImageError.InvalidKey else NodeImageError.SessionRequired,
                    detail,
                )

                // 413 is over the size cap, 415 an unsupported format, 422 a file it could not decode.
                it.code == 413 || it.code == 415 || it.code == 422 ->
                    throw NodeImageException(NodeImageError.Rejected(it.code), detail)

                else -> throw NodeImageException(NodeImageError.Http(it.code), detail)
            }
        }
    }

    private fun String.jsonObjectOrThrow() =
        runCatching { json.parseToJsonElement(this).jsonObject }
            .getOrElse { throw NodeImageException(NodeImageError.Unparsable, cause = it) }

    private companion object {
        val KEY_API_KEY = stringPreferencesKey("api-key")
    }
}

/**
 * A byte-array body that reports how much of itself has been written.
 *
 * OkHttp gives no upload-progress callback of its own, and wrapping the sink is the documented way
 * to get one. Written in chunks rather than one `write` call because a single write reports 0% and
 * then 100%, which makes the tray's ring a decoration instead of a signal.
 */
private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val contentType: okhttp3.MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = contentType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        var written = 0
        onProgress(0f)
        while (written < bytes.size) {
            val count = minOf(CHUNK_BYTES, bytes.size - written)
            sink.write(bytes, written, count)
            written += count
            onProgress(written.toFloat() / bytes.size)
        }
    }

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
    }
}
