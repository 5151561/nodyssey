package io.github.plaza.core.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val call =
            Request
                .Builder()
                .url(request.url)
                .apply { request.headers.forEach { (name, value) -> header(name, value) } }
                .method(request.method, request.body?.toRequestBody())
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

private fun HttpBody.toRequestBody(): RequestBody =
    when (this) {
        is HttpBody.Empty -> ByteArray(0).toRequestBody()

        is HttpBody.Text -> content.toRequestBody(contentType.toMediaType())

        is HttpBody.Multipart ->
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .apply { fields.forEach { (name, value) -> addFormDataPart(name, value) } }
                .addFormDataPart(fileField, fileName, fileBytes.toRequestBody(fileMimeType.toMediaType()))
                .build()
    }
