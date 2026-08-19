package io.github.plaza.core.net

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext

interface HtmlSource {
    suspend fun getHtml(path: String): String

    /**
     * Follows an HTTP redirect chain and returns the URL it ends on, or null if [path] answered
     * directly instead of redirecting.
     */
    suspend fun resolveRedirect(path: String): String?
}

/**
 * Fetches a forum's pages as HTML.
 *
 * Requests must be indistinguishable from the mobile site's own, otherwise Cloudflare answers with
 * a challenge instead of content — hence the browser headers and the shared cookie store, both of
 * which belong to the [HttpTransport] this is handed rather than to this class. What is here is the
 * part that is about the site: which headers a page request carries, and what an answer that is not
 * a page means.
 */
class SiteHtmlClient(
    private val transport: HttpTransport,
    private val dispatchers: AppDispatchers,
    private val config: SiteConfig,
    private val challengeDetector: ChallengeDetector = ChallengeDetector(config.markers),
) : HtmlSource {

    /** Returns the page body, or throws [SiteException] describing why it is unusable. */
    override suspend fun getHtml(path: String): String = withContext(dispatchers.io) {
        val response = transport.execute(pageRequest(path))
        challengeDetector.detect(response.body, response.code, response.headers)?.let { error ->
            throw SiteException(error)
        }
        response.body
    }

    override suspend fun resolveRedirect(path: String): String? = withContext(dispatchers.io) {
        val requestedUrl = absoluteUrl(path) ?: error("Invalid path: $path")
        transport.execute(pageRequest(path)).url.takeIf { it != requestedUrl }
    }

    private fun pageRequest(path: String): HttpRequest =
        HttpRequest(
            url = absoluteUrl(path) ?: error("Invalid path: $path"),
            headers =
            mapOf(
                "Accept" to config.htmlAccept,
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Upgrade-Insecure-Requests" to "1",
            ),
        )

    /**
     * Resolves site-relative paths against [SiteConfig.baseUrl]; leaves absolute URLs alone.
     *
     * A site with a richer URL vocabulary of its own is expected to build paths itself and hand them
     * here already shaped; this is only the last step before the request.
     */
    private fun absoluteUrl(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> null
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> config.baseUrl + trimmed
            else -> "${config.baseUrl}/$trimmed"
        }
    }
}
