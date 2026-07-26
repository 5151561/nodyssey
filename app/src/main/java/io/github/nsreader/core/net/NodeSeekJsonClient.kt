package io.github.nsreader.core.net

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.Selectors
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** A POST answer that survived transport and challenge classification; the status is the caller's to read. */
data class JsonPostResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * The handful of real JSON endpoints NodeSeek exposes. They do not cover browsing — lists and post
 * pages are still HTML — but they are authoritative where one exists, so prefer them over scraping.
 *
 * Endpoints under `/api/notification` and `/api/statistics` answer **500 when unauthenticated**
 * rather than 401. [NodeSeekJsonClient] maps that quirk to [NodeSeekError.LoginRequired] itself,
 * so callers see the same error a 401 would have produced.
 */
interface JsonSource {
    suspend fun getJson(path: String, referer: String = NodeSeekSite.BASE_URL + "/"): String

    /**
     * POST to a JSON endpoint with the same headers and challenge classification as [getJson].
     *
     * Unlike [getJson] the answer comes back with its status code instead of a thrown [NodeSeekError.Http]:
     * NodeSeek's write endpoints put meaningful JSON bodies on non-2xx statuses (a repeat sign-in is
     * an HTTP 500 whose body carries the sentence to show), so status-versus-body is the caller's
     * contract. Transport failures and Cloudflare interception still throw.
     */
    suspend fun postJson(path: String, referer: String = NodeSeekSite.BASE_URL + "/"): JsonPostResponse =
        throw UnsupportedOperationException("This JsonSource does not support POST")
}

class NodeSeekJsonClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : JsonSource {

    override suspend fun getJson(path: String, referer: String): String =
        withContext(dispatchers.io) {
            val response = execute(xhrRequest(path, referer).build())

            response.use {
                val body = it.body?.string().orEmpty()
                throwIfChallenge(it.header("cf-mitigated"), body)
                // Session-scoped endpoints answer 500, not 401, when the cookie is missing or stale.
                // "服务器错误" with a retry button would hide the one action that fixes it: signing in.
                if (it.code == 500 && isSessionScoped(path)) {
                    throw NodeSeekException(NodeSeekError.LoginRequired)
                }
                if (!it.isSuccessful) throw NodeSeekException(NodeSeekError.Http(it.code))
                body
            }
        }

    override suspend fun postJson(path: String, referer: String): JsonPostResponse =
        withContext(dispatchers.io) {
            val request = xhrRequest(path, referer)
                .header("Origin", NodeSeekSite.BASE_URL)
                .post(ByteArray(0).toRequestBody())
                .build()
            val response = execute(request)

            response.use {
                val body = it.body?.string().orEmpty()
                throwIfChallenge(it.header("cf-mitigated"), body)
                JsonPostResponse(code = it.code, body = body)
            }
        }

    private fun xhrRequest(path: String, referer: String): Request.Builder {
        val url = NodeSeekSite.absoluteUrl(path) ?: error("Invalid path: $path")
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", referer)
            // The site's own XHRs carry this; without it some endpoints answer with HTML.
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
    }

    private fun execute(request: Request) =
        try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw NodeSeekException(NodeSeekError.Network, e)
        }

    /**
     * Cloudflare intercepts a JSON call as challenge HTML, as a tagged `cf-mitigated: challenge`
     * header on non-HTML shapes, or as a 403 wrapping either — so this runs before any status
     * handling: "please verify" and "please sign in" send the user down different recovery paths.
     */
    private fun throwIfChallenge(cfMitigated: String?, body: String) {
        val isChallenge =
            cfMitigated?.equals("challenge", ignoreCase = true) == true ||
                Selectors.CLOUDFLARE_MARKERS.any(body::contains) ||
                // An HTML body on a JSON endpoint means Cloudflare intercepted the call.
                body.trimStart().startsWith("<")
        if (isChallenge) throw NodeSeekException(NodeSeekError.Cloudflare)
    }

    /** The endpoint families that only answer for a signed-in session — see the interface KDoc. */
    private fun isSessionScoped(path: String): Boolean =
        path.startsWith("/api/notification") || path.startsWith("/api/statistics")

    companion object {
        const val PATH_CATEGORIES = "/api/content/list-categories"
        const val PATH_UNREAD_COUNT = "/api/notification/unread-count"

        fun notificationListPath(type: String, page: Int = 1) =
            "/api/notification/$type/list?page=$page"

        fun accountInfoPath(uid: Long) = "/api/account/getInfo/$uid"

        fun discussionListPath(uid: Long, page: Int = 1) =
            "/api/content/list-discussions?uid=$uid&page=${page.coerceAtLeast(1)}"

        fun commentListPath(uid: Long, page: Int = 1) =
            "/api/content/list-comments?uid=$uid&page=${page.coerceAtLeast(1)}"

        /** Collections belong to the session, not to a uid: the site offers no one else's. */
        fun collectionListPath(page: Int = 1) =
            "/api/statistics/list-collection?page=${page.coerceAtLeast(1)}"

        /** `random` is the sign-in mode's wire value: "true" gambles, "false" takes a flat five. */
        fun attendancePath(random: String) = "/api/attendance?random=$random"

        fun attendanceBoardPath(page: Int = 1) =
            "/api/attendance/board?page=${page.coerceAtLeast(1)}"
    }
}
