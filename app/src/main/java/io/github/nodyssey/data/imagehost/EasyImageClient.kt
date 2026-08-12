package io.github.nodyssey.data.imagehost

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 简单图床 EasyImage 2.0 — a PHP uploader with no database, and no API past this one endpoint.
 *
 * `api/index.php` reads a `token` form field and an `image` file part, and answers **HTTP 200 for
 * everything**: it never sets a status code, so `{"result":"failed","code":204,…}` arrives looking
 * exactly like a success to anything that checks the status alone (upstream `api/index.php`, read
 * 2026-08-12). The `result` field is therefore the only thing worth believing.
 *
 * The user has to switch API uploading on in 设置 › 图床安全 › 高级设置 of their own installation
 * first — it ships off. Whatever that refusal looks like, it reaches the user as the host's own
 * sentence rather than as a guess from this end.
 */
internal class EasyImageClient(private val http: OkHttpClient) : ImageHostClient {

    override fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(TOKEN_FIELD, config.token.trim())
            .addImagePart(FILE_FIELD, upload, onProgress)
            .build()

        val request = Request.Builder()
            .url(config.siteUrl.normalizedSiteUrl() + UPLOAD_PATH)
            .header("Accept", "application/json")
            .post(body)
            .build()

        val root = http.readBody(request).asJsonObject()
        if (root.stringAt("result") != RESULT_SUCCESS) {
            throw ImageHostException(
                // Its own codes, not HTTP ones: 204 no file, 205 blocked by the IP list, 400 the
                // uploader could not process the file. All three are refusals of this request, and
                // none of them is a reason to make the user re-enter a token that worked yesterday.
                error = ImageHostError.Rejected(root.longAt("code")?.toInt() ?: 200),
                detail = root.stringAt("message"),
            )
        }
        val url = root.stringAt("url")
            ?: throw ImageHostException(ImageHostError.Unparsable)
        return HostedImage(
            id = root.stringAt("id", "srcName") ?: url,
            fileName = root.stringAt("srcName") ?: upload.fileName,
            url = url,
            sizeBytes = upload.bytes.size.toLong(),
        )
    }

    private companion object {
        const val UPLOAD_PATH = "/api/index.php"
        const val FILE_FIELD = "image"
        const val TOKEN_FIELD = "token"
        const val RESULT_SUCCESS = "success"
    }
}
