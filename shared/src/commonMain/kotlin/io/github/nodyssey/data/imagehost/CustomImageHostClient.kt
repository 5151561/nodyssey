package io.github.nodyssey.data.imagehost

import io.github.plaza.core.net.HttpTransport

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
internal class CustomImageHostClient(private val http: HttpTransport) : ImageHostClient {

    override suspend fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val fields = config.custom
        // Before the file: multipart parts go out in the order they were put in, so the credential is
        // on the wire before the megabytes are, and the progress ring starts at the image rather
        // than after a few text fields have already ticked it forward. A user who lists the same
        // field name twice gets one part rather than two, which is the one thing this loses against
        // the builder it replaced — and a form with two fields of a name is not a shape any of these
        // uploaders reads.
        val body = upload.multipart(fields.fileField.trim(), fields = fields.formParts().toMap())

        val request = postRequest(
            url = config.siteUrl.normalizedSiteUrl(),
            headers = buildMap {
                put("Accept", "application/json")
                if (fields.headerName.isNotBlank()) {
                    put(fields.headerName.trim(), fields.headerValue.trim())
                }
            },
            body = body,
        )

        val payload = http.readBody(request, onUploadProgress = onProgress)
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
