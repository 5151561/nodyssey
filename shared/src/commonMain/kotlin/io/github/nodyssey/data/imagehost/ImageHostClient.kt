package io.github.nodyssey.data.imagehost

import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest

/**
 * One image host's protocol, and nothing else.
 *
 * Stateless on purpose: the credential arrives as a [ImageHostConfig] parameter rather than being
 * held, so [ImageHostRepository] can own storage for all six hosts in one place and a client is
 * testable by handing it a config.
 *
 * **These are `suspend` functions written against `HttpTransport`, and until step D3c they were
 * blocking ones written against `OkHttpClient` in `:app`.** The thing that kept them there was
 * upload progress: the transport read a whole answer and said nothing while a request was in flight,
 * and the tray's ring needs the opposite. `HttpTransport.execute` now takes an
 * [io.github.plaza.core.net.UploadProgress], which is a change to a contract every other caller
 * shares — that is why it waited for a platform that actually needed it, and it is the only line of
 * this directory that is not a straight translation.
 *
 * [images] and [delete] default to refusing. Two of the six hosts genuinely publish no such
 * endpoint (see [ImageHostProvider.browsable]), and a default that throws is what keeps that from
 * being expressed as an empty list the screen would draw as "you have no images".
 */
internal interface ImageHostClient {
    suspend fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage

    suspend fun images(config: ImageHostConfig): List<HostedImage> =
        throw ImageHostException(ImageHostError.Unsupported)

    suspend fun delete(config: ImageHostConfig, image: HostedImage): Unit =
        throw ImageHostException(ImageHostError.Unsupported)
}

/**
 * The file part every one of these hosts wants, under whichever name that host reads it from, with
 * whatever text fields go beside it.
 *
 * [fields] before the file, which is the order [HttpBody.Multipart] writes them in: a credential
 * carried as a form field is on the wire before the megabytes are, and the progress ring starts at
 * the image rather than after a few text fields have already ticked it forward.
 *
 * The MIME type goes out as given. A host that answers 415 for one it dislikes is a better outcome
 * than a failure here, which is why nothing on this path validates it.
 */
internal fun ImageHostUpload.multipart(
    fileField: String,
    fields: Map<String, String> = emptyMap(),
): HttpBody.Multipart = HttpBody.Multipart(
    fields = fields,
    fileField = fileField,
    fileName = fileName,
    fileBytes = bytes,
    fileMimeType = mimeType,
)

/** A GET with the headers this host wants and nothing else — the shape five of the six start from. */
internal fun getRequest(url: String, headers: Map<String, String>): HttpRequest =
    HttpRequest(url = url, method = "GET", headers = headers)

internal fun postRequest(url: String, headers: Map<String, String>, body: HttpBody): HttpRequest =
    HttpRequest(url = url, method = "POST", headers = headers, body = body)

/**
 * A DELETE with an empty body rather than no body.
 *
 * [HttpBody.Empty] and `null` are two different requests on the wire — the first carries
 * `Content-Length: 0` and the second carries no length header at all. OkHttp's `Request.Builder.delete()`,
 * which the two hosts with a delete endpoint were written against, sent the first. Nothing here knows
 * whether either host cares; preserving what they have been answering since is cheaper than finding
 * out from a bug report.
 */
internal fun deleteRequest(url: String, headers: Map<String, String>): HttpRequest =
    HttpRequest(url = url, method = "DELETE", headers = headers, body = HttpBody.Empty)
