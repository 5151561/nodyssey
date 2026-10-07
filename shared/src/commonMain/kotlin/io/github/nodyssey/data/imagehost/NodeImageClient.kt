package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.NodeImageSite
import io.github.plaza.core.net.HttpTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * nodeimage.com — the host NodeSeek's own editor reaches through a browser extension.
 *
 * Every call here is authenticated by the API key alone. The website uses different,
 * cookie-authenticated paths for the same three things; the app deliberately takes the key ones,
 * because they need no OAuth round trip through NodeSeek and no shared browser session. See
 * [NodeImageSite.IMAGES_PATH] for how far apart the two families are.
 */
internal class NodeImageClient(private val http: HttpTransport) : ImageHostClient {

    override suspend fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val payload = http.readBody(
            postRequest(
                url(NodeImageSite.UPLOAD_PATH),
                headers(config),
                upload.multipart(NodeImageSite.UPLOAD_FILE_FIELD),
            ),
            onUploadProgress = onProgress,
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

    override suspend fun images(config: ImageHostConfig): List<HostedImage> {
        // `{"success":true,"count":33,"images":[…]}`, every image at once: `page` and `limit` are
        // ignored (checked 2026-10-08), so there is no paging to do.
        val root = http.readBody(getRequest(url(NodeImageSite.IMAGES_PATH), headers(config))).asJsonObject()
        val rows = runCatching { root.getValue("images").jsonArray }
            .getOrElse { throw ImageHostException(ImageHostError.Unparsable, cause = it) }
        return rows.mapNotNull { row -> runCatching { row.jsonObject }.getOrNull()?.toHostedImage() }
    }

    override suspend fun delete(config: ImageHostConfig, image: HostedImage) {
        try {
            http.readBody(deleteRequest(url(NodeImageSite.deletePath(image.deleteToken)), headers(config)))
        } catch (error: ImageHostException) {
            // 404 `IMAGE_NOT_FOUND`: already gone — deleted on the website, or by a second tap here.
            // Either way what the user asked for is true, and the row should leave the grid.
            if (error.error != ImageHostError.Http(404)) throw error
        }
    }

    /** One row of the v1 list — the same snake_case record, `links` and all, that upload answers. */
    private fun JsonObject.toHostedImage(): HostedImage? {
        val id = stringAt("image_id", "imageId") ?: return null
        return HostedImage(
            id = id,
            fileName = stringAt("filename") ?: id,
            url = stringAtPath("links.direct") ?: stringAt("url").orEmpty(),
            uploadTime = stringAt("upload_time", "uploadTime"),
            sizeBytes = longAt("size") ?: 0L,
            mimeType = stringAt("mimetype"),
        )
    }

    private fun url(path: String): String = NodeImageSite.absoluteApiUrl(path)

    private fun headers(config: ImageHostConfig): Map<String, String> = mapOf(
        "Accept" to "application/json",
        NodeImageSite.API_KEY_HEADER to config.token,
    )
}
