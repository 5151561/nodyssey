package io.github.plaza.core.net

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

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
 * a challenge instead of content — hence the browser headers and the shared WebView cookie jar.
 */
class SiteHtmlClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val config: SiteConfig,
    private val challengeDetector: ChallengeDetector = ChallengeDetector(config.markers),
) : HtmlSource {

    /** Returns the page body, or throws [SiteException] describing why it is unusable. */
    override suspend fun getHtml(path: String): String = withContext(dispatchers.io) {
        execute(path).use {
            val body = it.body.string()
            val headers = it.headers.toMultimap().mapValues { entry -> entry.value.joinToString(",") }
            challengeDetector.detect(body, it.code, headers)?.let { error ->
                throw SiteException(error)
            }
            body
        }
    }

    override suspend fun resolveRedirect(path: String): String? = withContext(dispatchers.io) {
        val requestedUrl = absoluteUrl(path) ?: error("Invalid path: $path")
        execute(path).use { it.request.url.toString() }
            .takeIf { it != requestedUrl }
    }

    private fun execute(path: String): Response {
        val url = absoluteUrl(path) ?: error("Invalid path: $path")
        val request = Request.Builder()
            .url(url)
            .header("Accept", config.htmlAccept)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        return try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw SiteException(SiteError.Network, e)
        }
    }

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
