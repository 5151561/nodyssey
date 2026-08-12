package io.github.nodyssey.data.imagehost

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Whatever the user is running, described field by field.
 *
 * The five clients beside this one each encode a protocol the app was taught. This one encodes none:
 * it posts a multipart form to an address the user gave, adds the header and the extra form fields
 * they listed, and reads the link out of the answer at the path they wrote. It is the same contract
 * PicGo's "自定义 Web 图床" offers, and it is here so that using a host nobody has heard of does not
 * mean waiting for a release.
 *
 * Because nothing about the answer is known in advance, a path that finds nothing carries the first
 * [DETAIL_CHARS] characters of the body into the error. The settings screen shows that text — with a
 * host this app cannot introspect, seeing what actually came back is the only way to fix the path.
 */
internal class CustomImageHostClient(private val http: OkHttpClient) : ImageHostClient {

    override fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val fields = config.custom
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        // Before the file: multipart parts go out in the order they are added, so the credential is
        // on the wire before the megabytes are, and the progress ring starts at the image rather
        // than after a few text fields have already ticked it forward.
        fields.formParts().forEach { (name, value) -> body.addFormDataPart(name, value) }
        body.addImagePart(fields.fileField.trim(), upload, onProgress)

        val request = Request.Builder()
            .url(config.siteUrl.normalizedSiteUrl())
            .header("Accept", "application/json")
            .apply {
                if (fields.headerName.isNotBlank()) {
                    header(fields.headerName.trim(), fields.headerValue.trim())
                }
            }.post(body.build())
            .build()

        val payload = http.readBody(request)
        val link = runCatching { imageHostJson.parseToJsonElement(payload) }
            .getOrNull()
            ?.stringAtPath(fields.urlPath.trim())
            ?: throw ImageHostException(ImageHostError.Unparsable, detail = payload.take(DETAIL_CHARS))

        return HostedImage(
            id = link,
            fileName = upload.fileName,
            url = link.withPrefix(fields.urlPrefix.trim()),
            sizeBytes = upload.bytes.size.toLong(),
        )
    }
}

/**
 * `/i/2026/08/a.webp` + `https://img.example.com` → the whole URL.
 *
 * Plenty of self-hosted uploaders answer with a path relative to their own root, and a relative URL
 * written into a NodeSeek post resolves against *nodeseek.com* — a broken image with no clue as to
 * why. A link that is already absolute is left exactly as it is.
 */
internal fun String.withPrefix(prefix: String): String = when {
    prefix.isEmpty() -> this
    startsWith("http://") || startsWith("https://") -> this
    else -> prefix.trimEnd('/') + "/" + trimStart('/')
}
