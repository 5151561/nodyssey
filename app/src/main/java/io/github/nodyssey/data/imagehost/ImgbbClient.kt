package io.github.nodyssey.data.imagehost

import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * imgbb — public, free, and the simplest of the six: one endpoint, and the key rides in the query
 * string rather than a header (its API reference, read 2026-08-12).
 *
 * Upload is all it publishes. There is no listing endpoint and no delete endpoint — an upload
 * answers with a `delete_url`, but that is a page a human opens, not something to call — so 图床管理
 * says as much for this host instead of pretending the account is empty.
 */
internal class ImgbbClient(private val http: OkHttpClient) : ImageHostClient {

    override fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addImagePart(FILE_FIELD, upload, onProgress)
            .addFormDataPart(NAME_FIELD, upload.fileName)
            .build()

        val url = UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter(KEY_PARAM, config.token.trim())
            .build()

        val payload = http.readBody(Request.Builder().url(url).post(body).build())
        val root = payload.asJsonObject()
        val data = runCatching { root["data"]!!.jsonObject }.getOrNull()
            ?: throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS))

        // `display_url` is the resized copy this host serves for embedding; `url` is the original.
        // The original is what belongs in a post — the forum decides how wide to draw it.
        val link = data.stringAt("url", "display_url")
            ?: throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS))
        return HostedImage(
            id = data.stringAt("id") ?: link,
            fileName = data.stringAt("title") ?: upload.fileName,
            url = link,
            sizeBytes = data.longAt("size") ?: upload.bytes.size.toLong(),
        )
    }

    private companion object {
        const val UPLOAD_URL = "https://api.imgbb.com/1/upload"
        const val KEY_PARAM = "key"
        const val FILE_FIELD = "image"
        const val NAME_FIELD = "name"
    }
}
