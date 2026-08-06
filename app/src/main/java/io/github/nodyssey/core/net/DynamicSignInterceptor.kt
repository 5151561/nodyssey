package io.github.nodyssey.core.net

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.security.MessageDigest

/**
 * Signs NodeSeek's vote endpoints, which reject an unsigned request outright.
 *
 * The site's vote module wraps its own fetch client in a `beforeRequest` hook that sets
 * `x-dynamic-sign` to the hex SHA-1 of
 *
 * ```
 * method + "\n\n" + absoluteUrl + "\n\n" + userAgent + "\n\n" + body
 * ```
 *
 * Without the header every call under `/api/vote/` answers `403 {"success":false}` — including the plain
 * read, and including a signed-out one. It is the header's *presence* the server currently checks: a
 * bogus forty-character value is accepted, exactly as [io.github.nodyssey.data.composer] finds for
 * `Csrf-Token`. The real digest is computed anyway, because it costs one `MessageDigest` and it is
 * the only version that keeps working if the check is ever tightened.
 *
 * **This must run after the interceptor that sets `User-Agent`.** The server recomputes the digest
 * from the request it received, so the string signed here and the string sent have to be the same
 * one — which is why this reads the header off the chain rather than being handed the value.
 *
 * Scoped to `/api/vote/`: no other endpoint family asks for it, and the site's own client does not
 * send it elsewhere.
 */
class DynamicSignInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith(SIGNED_PATH_PREFIX)) return chain.proceed(request)

        val body =
            request.body?.let { body ->
                Buffer().also(body::writeTo).readUtf8()
            }.orEmpty()
        val payload =
            listOf(
                request.method,
                request.url.toString(),
                request.header("User-Agent").orEmpty(),
                body,
            ).joinToString(SEPARATOR)

        return chain.proceed(
            request
                .newBuilder()
                .header(HEADER, sha1Hex(payload))
                .build(),
        )
    }

    private fun sha1Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val HEADER = "x-dynamic-sign"
        const val SIGNED_PATH_PREFIX = "/api/vote/"

        /** Two newlines, as the site's own `[method, url, ua, body].join("\n\n")` produces. */
        const val SEPARATOR = "\n\n"
    }
}
