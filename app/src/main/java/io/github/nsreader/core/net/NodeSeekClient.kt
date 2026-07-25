package io.github.nsreader.core.net

import io.github.nsreader.core.NodeSeekSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches NodeSeek pages as HTML.
 *
 * Requests must be indistinguishable from the mobile site's own, otherwise Cloudflare answers with
 * a challenge instead of content — hence the browser headers and the shared WebView cookie jar.
 */
class NodeSeekClient(
    private val okHttpClient: OkHttpClient,
) {

    /** Runs on IO and returns the page body, or throws [NodeSeekException] for a challenge. */
    suspend fun getHtml(path: String): String = withContext(Dispatchers.IO) {
        val url = NodeSeekSite.absoluteUrl(path) ?: error("Invalid path: $path")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NodeSeekSite.USER_AGENT)
            .header("Accept", NodeSeekSite.HTML_ACCEPT)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", NodeSeekSite.BASE_URL + "/")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val headers = response.headers.toMultimap().mapValues { it.value.joinToString(",") }
            ChallengeDetector.detect(body, response.code, headers)?.let { challenge ->
                throw NodeSeekException.of(challenge)
            }
            body
        }
    }
}
