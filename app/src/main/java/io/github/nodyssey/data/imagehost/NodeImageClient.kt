package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.NodeImageSite
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * nodeimage.com — the host NodeSeek's own editor reaches through a browser extension.
 *
 * The endpoints are the ones the site documents on its own API page (read 2026-07-28). Its web
 * uploader uses a different, cookie-authenticated path (`POST /upload`); the app deliberately takes
 * the documented key-authenticated one instead, because it needs no OAuth round trip through
 * NodeSeek and no shared browser session.
 */
internal class NodeImageClient(private val http: OkHttpClient) : ImageHostClient {

    override fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addImagePart(NodeImageSite.UPLOAD_FILE_FIELD, upload, onProgress)
            .build()

        val payload = http.readBody(
            request(config, NodeImageSite.UPLOAD_PATH).post(body).build(),
            keyIsEnough = true,
        )
        val root = payload.asJsonObject()
        // The host answers 200 with `success:false` for a rejection it can describe, so a status
        // check alone would report a failed upload as a successful one with an empty URL.
        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
            throw ImageHostException(
                error = ImageHostError.Rejected(200),
                detail = root.stringAt("message"),
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
        val url = root.stringAtPath("links.direct")
            ?: root.stringAt("url")
            ?: throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS))
        return HostedImage(
            id = root.stringAt("image_id", "imageId").orEmpty(),
            fileName = root.stringAt("filename") ?: upload.fileName,
            url = url,
            sizeBytes = root.longAt("size") ?: upload.bytes.size.toLong(),
        )
    }

    override fun images(config: ImageHostConfig): List<HostedImage> {
        val payload = http.readBody(
            request(config, NodeImageSite.IMAGES_PATH).get().build(),
            keyIsEnough = false,
        )
        val element = runCatching { imageHostJson.parseToJsonElement(payload) }
            .getOrElse { throw ImageHostException(ImageHostError.Unparsable, cause = it) }
        // A bare array today, but a paged `{images:[…]}` is the obvious next shape for this host, so
        // both are read rather than letting a server-side change empty the screen without a word.
        val rows = runCatching {
            element.jsonArray
        }.recoverCatching {
            element.jsonObject["images"]?.jsonArray ?: error("no images array")
        }.getOrElse { throw ImageHostException(ImageHostError.Unparsable, cause = it) }

        return rows.mapNotNull { row ->
            val obj = runCatching { row.jsonObject }.getOrNull() ?: return@mapNotNull null
            obj.toHostedImage()
        }
    }

    override fun delete(config: ImageHostConfig, image: HostedImage) {
        http.readBody(
            request(config, NodeImageSite.imagePath(image.deleteToken)).delete().build(),
            keyIsEnough = false,
        )
    }

    private fun JsonObject.toHostedImage(): HostedImage? {
        val id = stringAt("imageId", "image_id") ?: return null
        return HostedImage(
            id = id,
            fileName = stringAt("filename") ?: id,
            url = stringAt("url").orEmpty(),
            uploadTime = stringAt("uploadTime"),
            sizeBytes = longAt("size") ?: 0L,
            mimeType = stringAt("mimetype"),
        )
    }

    private fun request(config: ImageHostConfig, path: String): Request.Builder =
        Request.Builder()
            .url(NodeImageSite.absoluteApiUrl(path))
            .header("Accept", "application/json")
            .header(NodeImageSite.API_KEY_HEADER, config.token)
}
