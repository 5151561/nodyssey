package io.github.nodyssey.core.net

import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import io.github.plaza.core.net.WebUrl
import okio.ByteString.Companion.encodeUtf8

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
 * Without the header every call under `/api/vote/` answers `403 {"success":false}` — including the
 * plain read, and including a signed-out one. It is the header's *presence* the server currently
 * checks: a bogus forty-character value is accepted, exactly as [io.github.nodyssey.data.composer]
 * finds for `Csrf-Token`. The real digest is computed anyway, because it costs one hash and it is the
 * only version that keeps working if the check is ever tightened.
 *
 * **This was an OkHttp interceptor in `:app` until now, and the reason it stayed there was wrong.**
 * Step A5 recorded it as "commonMain would need SHA-1, Kotlin has no common crypto API, and both
 * platforms' C functions are deprecated — so the remaining option is hand-writing a digest". The
 * remaining option was in fact [okio], which this module already compiles against on all four
 * targets: `ByteString.sha1()` is pure Kotlin in `commonMain` there. The debt was one import wide.
 *
 * **The `User-Agent` is set here rather than read.** The server recomputes the digest from the
 * request it received, so the string signed and the string sent have to be the same one. The
 * interceptor this replaces got that by running *after* `BrowserHeadersInterceptor` and reading the
 * finished request off the chain — a guarantee that lived in the order two builder calls appear in.
 * A transport decorator has no chain to read, so it states the value instead: `BrowserHeadersInterceptor`
 * fills a header in only when the caller left it empty, and `NSURLSession` lets a request override
 * `HTTPAdditionalHeaders`, so writing it on the request is what makes both platforms send what was
 * signed. A caller that set its own is left alone and signed with the one it set.
 *
 * Scoped to `/api/vote/`: no other endpoint family asks for it, and the site's own client does not
 * send it elsewhere. Everything else goes straight through, which is why this can sit under the whole
 * app rather than only under [NodeSeekJsonClient].
 */
class DynamicSignTransport(
    private val delegate: HttpTransport,
    private val userAgent: String,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        if (!isSigned(request.url)) return delegate.execute(request, onUploadProgress)

        val agent = request.headers.entries.firstOrNull { it.key.equals(USER_AGENT, ignoreCase = true) }?.value
            ?: userAgent
        val signature = dynamicSign(
            method = request.method,
            url = request.url,
            userAgent = agent,
            body = request.body.signedText(),
        )
        return delegate.execute(
            request.copy(headers = request.headers + mapOf(USER_AGENT to agent, HEADER to signature)),
            onUploadProgress,
        )
    }

    private companion object {
        const val USER_AGENT = "User-Agent"
    }
}

private const val HEADER = "x-dynamic-sign"

/**
 * Whether [url] addresses the one endpoint family that asks to be signed.
 *
 * Read off the path of an absolute URL rather than off a matched prefix, so that a query string
 * carrying `/api/vote/` — or a host with it in the path of a `?to=` — is not mistaken for one.
 */
private fun isSigned(url: String): Boolean =
    WebUrl
        .parse(url)
        ?.path
        ?.startsWith(SIGNED_PATH_PREFIX) == true

internal fun dynamicSign(method: String, url: String, userAgent: String, body: String): String =
    listOf(method, url, userAgent, body)
        .joinToString(SEPARATOR)
        .encodeUtf8()
        .sha1()
        .hex()

/**
 * The body as the site's own hook would see it — the string it passed to `fetch`.
 *
 * A multipart body has no such string, and no signed endpoint takes one: every `/api/vote/` call is
 * a JSON POST or a bare GET. Signing the empty string for one is therefore a case that cannot arise
 * rather than a shortcut, and if it ever did the server would answer 403 rather than accept a wrong
 * digest silently.
 */
private fun HttpBody?.signedText(): String =
    when (this) {
        null, is HttpBody.Empty -> ""
        is HttpBody.Text -> content
        is HttpBody.Multipart -> ""
    }

private const val SIGNED_PATH_PREFIX = "/api/vote/"

/** Two newlines, as the site's own `[method, url, ua, body].join("\n\n")` produces. */
private const val SEPARATOR = "\n\n"
