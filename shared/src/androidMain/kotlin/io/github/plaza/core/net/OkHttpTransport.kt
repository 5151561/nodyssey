package io.github.plaza.core.net

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException

/**
 * [HttpTransport] on OkHttp — the Android side of the one contract everything above the network
 * layer is written against.
 *
 * Everything that used to be configured per call and is now configured per client stays outside
 * this class: the cookie jar, the proxy selector, the browser headers, the timeouts. They are on the
 * [OkHttpClient] this is handed, which is where the app assembles them, and this file's whole job is
 * translation.
 *
 * No `withContext` here. The dispatcher is chosen one level up, by the client that knows the call is
 * blocking work — see [SiteHtmlClient] — and adding a second hop would only make the stack deeper.
 */
class OkHttpTransport(
    private val okHttpClient: OkHttpClient,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        val call =
            Request
                .Builder()
                .url(request.url)
                .apply { request.headers.forEach { (name, value) -> header(name, value) } }
                .method(request.method, request.body?.toRequestBody(onUploadProgress))
                .build()

        return try {
            okHttpClient.newCall(call).execute().use { response ->
                HttpResponse(
                    code = response.code,
                    // The URL the answer came from: OkHttp follows redirects itself and rewrites the
                    // request as it goes, so this is the last hop rather than the one asked for.
                    url = response.request.url.toString(),
                    headers =
                    response.headers
                        .toMultimap()
                        .entries
                        .associate { (name, values) -> name.lowercase() to values.joinToString(",") },
                    body = response.body.string(),
                )
            }
        } catch (e: IOException) {
            throw SiteException(SiteError.Network, e)
        }
    }
}

private fun HttpBody.toRequestBody(onUploadProgress: UploadProgress?): RequestBody =
    when (this) {
        is HttpBody.Empty -> ByteArray(0).toRequestBody()

        is HttpBody.Text -> content.toRequestBody(contentType.toMediaType())

        is HttpBody.Multipart ->
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .apply { fields.forEach { (name, value) -> addFormDataPart(name, value) } }
                .addFormDataPart(
                    fileField,
                    fileName,
                    if (onUploadProgress == null) {
                        fileBytes.toRequestBody(fileMimeType.toMediaType())
                    } else {
                        ProgressRequestBody(fileBytes, fileMimeType.toMediaType(), onUploadProgress)
                    },
                ).build()
    }

/**
 * A byte-array body that reports how much of itself has been written.
 *
 * OkHttp publishes no upload-progress callback of its own, and wrapping the body is the documented
 * way to get one. Written in chunks rather than in one `write` call because a single write reports
 * 0% and then 100%, which makes the tray's ring a decoration instead of a signal.
 *
 * On the file part only. A multipart body's text fields are a few dozen bytes against an image's few
 * million, and a ring that ticks forward before the photo starts moving is lying about which part is
 * slow. That is also the order the parts go out in — see the custom host's client.
 */
private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val contentType: MediaType,
    private val onUploadProgress: UploadProgress,
) : RequestBody() {
    override fun contentType() = contentType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        var written = 0
        onUploadProgress(0f)
        while (written < bytes.size) {
            val count = minOf(CHUNK_BYTES, bytes.size - written)
            sink.write(bytes, written, count)
            written += count
            onUploadProgress(written.toFloat() / bytes.size)
        }
        // An empty file would have skipped the loop entirely, and [UploadProgress] promises a caller
        // that hears anything hears 1f.
        if (bytes.isEmpty()) onUploadProgress(1f)
    }

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
    }
}
