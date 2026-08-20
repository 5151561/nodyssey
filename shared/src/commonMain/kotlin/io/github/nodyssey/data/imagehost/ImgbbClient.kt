package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.urlEncode
import io.github.plaza.core.net.HttpTransport
import kotlinx.serialization.json.jsonObject

/**
 * imgbb — public, free, and the simplest of the six: one endpoint, and the key rides in the query
 * string rather than a header (its API reference, read 2026-08-12).
 *
 * Upload is all it publishes. There is no listing endpoint and no delete endpoint — an upload
 * answers with a `delete_url`, but that is a page a human opens, not something to call — so 图床管理
 * says as much for this host instead of pretending the account is empty.
 */
internal class ImgbbClient(private val http: HttpTransport) : ImageHostClient {

    override suspend fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        // The name field goes beside the file rather than after it — see [multipart].
        val body = upload.multipart(FILE_FIELD, fields = mapOf(NAME_FIELD to upload.fileName))
        val url = "$UPLOAD_URL?$KEY_PARAM=${config.token.trim().urlEncode()}"

        val payload = http.readBody(
            // No `Accept`, unlike the other five: this host answers JSON to everything and the
            // request it has been served since release did not carry one.
            postRequest(url, headers = emptyMap(), body = body),
            onUploadProgress = onProgress,
        )
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
