package io.github.nodyssey.data.imagehost

import io.github.plaza.core.net.HttpTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/**
 * 兰空图床 Lsky Pro V2 — somebody's own server, reached with a token from its 设置 › 接口 page.
 *
 * The four routes below are the ones `routes/api.php` declares in the upstream repository (read
 * 2026-08-12): upload sits behind the "is the API enabled" middleware only, while listing and
 * deleting additionally want a Sanctum token — the same token, so a working upload does not imply a
 * working list if the admin has issued a scoped one.
 *
 * Every answer is wrapped in `{"status":bool,"message":string,"data":…}` and arrives with HTTP 200
 * even when `status` is false, so the envelope is checked before anything is read out of it.
 */
internal class LskyProClient(private val http: HttpTransport) : ImageHostClient {

    override suspend fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val payload = http.readBody(
            postRequest(url(config, UPLOAD_PATH), headers(config), upload.multipart(FILE_FIELD)),
            onUploadProgress = onProgress,
        )
        val data = payload.unwrap()
        return data.toHostedImage() ?: throw ImageHostException(
            ImageHostError.Unparsable,
            detail = payload.take(DETAIL_CHARS),
        )
    }

    override suspend fun images(config: ImageHostConfig): List<HostedImage> {
        val payload = http.readBody(getRequest(url(config, IMAGES_PATH), headers(config)))
        // Laravel pagination: the envelope's `data` is the page object, and the page's own `data` is
        // the row array. A flat array is accepted too so a future unpaged answer does not empty the
        // screen without a word.
        val page = payload.unwrap()
        val rows = runCatching { page["data"]!!.jsonArray }
            .getOrElse { throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS)) }
        return rows.mapNotNull { row ->
            runCatching { row.jsonObject }.getOrNull()?.toHostedImage()
        }
    }

    override suspend fun delete(config: ImageHostConfig, image: HostedImage) {
        val payload = http.readBody(
            deleteRequest(url(config, "$IMAGES_PATH/${image.deleteToken}"), headers(config)),
        )
        payload.unwrapOrThrow()
    }

    /** `{"status":true,"data":{…}}` → the `data` object, or the host's own sentence as an error. */
    private fun String.unwrap(): JsonObject {
        unwrapOrThrow()
        return runCatching { asJsonObject()["data"]!!.jsonObject }
            .getOrElse { throw ImageHostException(ImageHostError.Unparsable, detail = take(DETAIL_CHARS)) }
    }

    private fun String.unwrapOrThrow() {
        val root = asJsonObject()
        if (root["status"]?.jsonPrimitive?.booleanOrNull == false) {
            // 200 with status:false is how this host says "over quota", "format not allowed", and
            // "that strategy does not exist" — all of them refusals of the file, not of the token.
            throw ImageHostException(ImageHostError.Rejected(200), detail = root.stringAt("message"))
        }
    }

    private fun JsonObject.toHostedImage(): HostedImage? {
        val url = stringAtPath("links.url") ?: return null
        val key = stringAt("key") ?: return null
        return HostedImage(
            id = key,
            fileName = stringAt("origin_name", "name") ?: key,
            url = url,
            uploadTime = stringAt("date", "human_date"),
            sizeBytes = kilobytes(),
            mimeType = stringAt("mimetype"),
        )
    }

    /**
     * Lsky reports `size` in **kilobytes**, as a float — `ImageService` stores `getSize() / 1024`
     * (upstream, read 2026-08-12). Read as bytes it turns a 2 MB photo into a 2 KB one, and the
     * 图床管理 total then reads as a rounding error.
     */
    private fun JsonObject.kilobytes(): Long =
        runCatching { this["size"]!!.jsonPrimitive.doubleOrNull }.getOrNull()
            ?.let { (it * BYTES_PER_KB).roundToLong() }
            ?: 0L

    private fun url(config: ImageHostConfig, path: String): String =
        config.siteUrl.normalizedSiteUrl() + path

    private fun headers(config: ImageHostConfig): Map<String, String> = mapOf(
        // Accept matters more than usual here: without it Laravel answers a validation failure
        // with an HTML redirect instead of JSON, which reads as Unparsable rather than as the
        // sentence the host was trying to say.
        "Accept" to "application/json",
        "Authorization" to config.token.asBearer(),
    )

    private companion object {
        const val UPLOAD_PATH = "/api/v1/upload"
        const val IMAGES_PATH = "/api/v1/images"
        const val FILE_FIELD = "file"
        const val BYTES_PER_KB = 1024
    }
}

/**
 * Tokens get copied out of Lsky's own page with the `Bearer ` prefix about as often as without it,
 * and sending `Bearer Bearer …` is an indistinguishable 401. Adding it only when it is missing costs
 * one comparison and removes the most common way to mis-paste this particular field.
 */
internal fun String.asBearer(): String {
    val token = trim()
    return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
}
