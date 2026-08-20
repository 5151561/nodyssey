package io.github.nodyssey.data.imagehost

import io.github.plaza.core.net.HttpTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * sm.ms — public, free, and the only host here the user does not have to run themselves.
 *
 * The upload shape is the one PicGo's own uploader speaks (`smms.ts`, read 2026-08-12): the token
 * goes in a bare `Authorization` header with **no `Bearer` prefix**, the file goes in `smfile`, and
 * the URL comes back at `data.url`. Note the exception to the usual envelope rule — this host does
 * not use `Bearer`, so the token is sent exactly as pasted.
 *
 * Caveat, stated because it decides how much to trust the rest: `doc.sm.ms` was unreachable from the
 * build machine when this was written, so the two paths below that PicGo does not exercise — the
 * upload history, and the duplicate-upload answer — are written from community implementations
 * rather than from the specification. Both fail soft: a shape that does not match surfaces the
 * host's own `message`, never a wrong URL.
 */
internal class SmmsClient(private val http: HttpTransport) : ImageHostClient {

    override suspend fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val payload = http.readBody(
            postRequest(url(UPLOAD_PATH), headers(config), upload.multipart(FILE_FIELD)),
            onUploadProgress = onProgress,
        )
        val root = payload.asJsonObject()
        val data = runCatching { root["data"]!!.jsonObject }.getOrNull()

        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
            /*
             * Re-uploading a file this account already holds is a refusal, not a failure: the host
             * answers `code:"image_repeated"` and hands back the existing link instead of storing a
             * second copy. Treating it as an error would make the second attempt at the same photo
             * look broken, so the existing URL is used and the post gets its image.
             */
            val existing = root.stringAt("images") ?: data?.stringAt("url")
            if (root.stringAt("code") == CODE_REPEATED && existing != null) {
                return HostedImage(id = existing, fileName = upload.fileName, url = existing)
            }
            throw ImageHostException(ImageHostError.Rejected(200), detail = root.stringAt("message"))
        }

        val url = data?.stringAt("url")
            ?: throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS))
        return HostedImage(
            id = data.stringAt("hash") ?: url,
            fileName = data.stringAt("filename") ?: upload.fileName,
            url = url,
            sizeBytes = data.longAt("size") ?: upload.bytes.size.toLong(),
            // Deleting takes the per-image hash, not the filename or the storename; sending anything
            // else answers success and removes nothing.
            deleteToken = data.stringAt("hash").orEmpty(),
        )
    }

    override suspend fun images(config: ImageHostConfig): List<HostedImage> {
        val payload = http.readBody(getRequest(url(HISTORY_PATH), headers(config)))
        val rows = runCatching { payload.asJsonObject()["data"]!!.jsonArray }
            .getOrElse { throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS)) }
        return rows.mapNotNull { row ->
            runCatching { row.jsonObject }.getOrNull()?.toHostedImage()
        }
    }

    override suspend fun delete(config: ImageHostConfig, image: HostedImage) {
        if (image.deleteToken.isBlank()) throw ImageHostException(ImageHostError.Unsupported)
        // A GET, oddly, but that is the route this host publishes for it.
        val payload = http.readBody(getRequest(url("$DELETE_PATH/${image.deleteToken}"), headers(config)))
        val root = payload.asJsonObject()
        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
            throw ImageHostException(ImageHostError.Rejected(200), detail = root.stringAt("message"))
        }
    }

    private fun JsonObject.toHostedImage(): HostedImage? {
        val url = stringAt("url") ?: return null
        val hash = stringAt("hash")
        return HostedImage(
            id = hash ?: url,
            fileName = stringAt("filename", "storename") ?: url.substringAfterLast('/'),
            url = url,
            uploadTime = stringAt("created_at"),
            sizeBytes = longAt("size") ?: 0L,
            deleteToken = hash.orEmpty(),
        )
    }

    private fun url(path: String): String = BASE_URL + path

    private fun headers(config: ImageHostConfig): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Authorization" to config.token.trim(),
    )

    private companion object {
        const val BASE_URL = "https://sm.ms"
        const val UPLOAD_PATH = "/api/v2/upload"
        const val HISTORY_PATH = "/api/v2/upload_history"
        const val DELETE_PATH = "/api/v2/delete"
        const val FILE_FIELD = "smfile"
        const val CODE_REPEATED = "image_repeated"
    }
}
