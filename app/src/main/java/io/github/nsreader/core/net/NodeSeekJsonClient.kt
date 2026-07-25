package io.github.nsreader.core.net

import io.github.nsreader.core.NodeSeekSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The handful of real JSON endpoints NodeSeek exposes. They do not cover browsing — lists and post
 * pages are still HTML — but they are authoritative for things like the board list, so prefer them
 * over scraping wherever one exists.
 */
class NodeSeekJsonClient(
    private val okHttpClient: OkHttpClient,
) {

    suspend fun getJson(path: String, referer: String = "${NodeSeekSite.BASE_URL}/"): String =
        withContext(Dispatchers.IO) {
            val url = NodeSeekSite.absoluteUrl(path) ?: error("Invalid path: $path")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", referer)
                // The site's own XHRs carry this; without it some endpoints answer with HTML.
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw NodeSeekException.of(ChallengeDetector.Challenge.Blocked(response.code))
                }
                // An HTML body on a JSON endpoint means Cloudflare intercepted the call.
                if (body.trimStart().startsWith("<")) {
                    throw NodeSeekException.of(ChallengeDetector.Challenge.Cloudflare)
                }
                body
            }
        }

    companion object {
        const val PATH_CATEGORIES = "/api/content/list-categories"
        const val PATH_UNREAD_COUNT = "/api/notification/unread-count"

        fun accountInfoPath(uid: Long) = "/api/account/getInfo/$uid"
    }
}
