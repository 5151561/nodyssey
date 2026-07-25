package io.github.nsreader.core.net

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The handful of real JSON endpoints NodeSeek exposes. They do not cover browsing — lists and post
 * pages are still HTML — but they are authoritative where one exists, so prefer them over scraping.
 *
 * Endpoints under `/api/notification` and `/api/statistics` answer **500 when unauthenticated**
 * rather than 401, so callers must treat any failure as possibly meaning "sign in first".
 */
interface JsonSource {
    suspend fun getJson(path: String, referer: String = NodeSeekSite.BASE_URL + "/"): String
}

class NodeSeekJsonClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : JsonSource {

    override suspend fun getJson(path: String, referer: String): String =
        withContext(dispatchers.io) {
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

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw NodeSeekException(NodeSeekError.Network, e)
            }

            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw NodeSeekException(NodeSeekError.Http(it.code))
                // An HTML body on a JSON endpoint means Cloudflare intercepted the call.
                if (body.trimStart().startsWith("<")) {
                    throw NodeSeekException(NodeSeekError.Cloudflare)
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
