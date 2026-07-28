package io.github.nodyssey.core.net

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

interface HtmlSource {
    suspend fun getHtml(path: String): String
}

/**
 * Fetches NodeSeek pages as HTML.
 *
 * Requests must be indistinguishable from the mobile site's own, otherwise Cloudflare answers with
 * a challenge instead of content — hence the browser headers and the shared WebView cookie jar.
 */
class NodeSeekClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : HtmlSource {

    /** Returns the page body, or throws [NodeSeekException] describing why it is unusable. */
    override suspend fun getHtml(path: String): String = withContext(dispatchers.io) {
        val url = NodeSeekSite.absoluteUrl(path) ?: error("Invalid path: $path")
        val request = Request.Builder()
            .url(url)
            .header("Accept", NodeSeekSite.HTML_ACCEPT)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw NodeSeekException(NodeSeekError.Network, e)
        }

        response.use {
            val body = it.body?.string().orEmpty()
            val headers = it.headers.toMultimap().mapValues { entry -> entry.value.joinToString(",") }
            ChallengeDetector.detect(body, it.code, headers)?.let { error ->
                throw NodeSeekException(error)
            }
            body
        }
    }
}
