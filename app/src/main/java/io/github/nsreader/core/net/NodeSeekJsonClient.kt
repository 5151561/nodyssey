package io.github.nsreader.core.net

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/**
 * Writes, kept apart from [JsonSource] so a repository that only reads cannot accidentally post —
 * and so the many read-only test doubles stay as small as they are.
 */
interface JsonWriteSource {
    /**
     * Sends a JSON body and returns the JSON answer.
     *
     * Unlike [JsonSource.getJson] this maps 401/403 to [NodeSeekError.LoginRequired]: a write is the
     * one moment where a session that quietly expired has to be told apart from a server fault,
     * because the recovery is "sign in and send again" rather than "retry".
     */
    suspend fun postJson(
        path: String,
        body: String,
        referer: String = NodeSeekSite.BASE_URL + "/",
    ): String
}

/** Both halves, for the repositories that read a list and then add to it. */
interface JsonApi :
    JsonSource,
    JsonWriteSource

class NodeSeekJsonClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : JsonApi {

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

    override suspend fun postJson(path: String, body: String, referer: String): String =
        withContext(dispatchers.io) {
            val url = NodeSeekSite.absoluteUrl(path) ?: error("Invalid path: $path")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/json")
                .header("Origin", NodeSeekSite.BASE_URL)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw NodeSeekException(NodeSeekError.Network, e)
            }

            response.use {
                val payload = it.body?.string().orEmpty()
                // Cloudflare answers a blocked write with 403 plus challenge HTML, so the shape of the
                // body decides before the status code does — "verify" and "sign in" are different fixes.
                if (payload.trimStart().startsWith("<")) {
                    throw NodeSeekException(NodeSeekError.Cloudflare)
                }
                if (it.code == 401 || it.code == 403) {
                    throw NodeSeekException(NodeSeekError.LoginRequired)
                }
                if (!it.isSuccessful) throw NodeSeekException(NodeSeekError.Http(it.code))
                payload
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val PATH_CATEGORIES = "/api/content/list-categories"
        const val PATH_UNREAD_COUNT = "/api/notification/unread-count"

        fun notificationListPath(type: String, page: Int = 1) =
            "/api/notification/$type/list?page=$page"

        fun accountInfoPath(uid: Long) = "/api/account/getInfo/$uid"

        /** `?all=true` with an empty body; the per-item form takes a JSON array we do not need. */
        fun markAllViewedPath(type: String) = "/api/notification/$type/markViewed?all=true"

        /*
         * Direct messages.
         *
         * Read out of the site's own `notification.js` on a signed-in device (2026-07-26) rather
         * than inferred: `list` returns one row per conversation holding only its latest message,
         * `with/{uid}` returns the full history plus a `talkTo` header, and `send` is a POST whose
         * recipient field is camel-cased `receiverUid` while every other field on this API is
         * snake_case. A `message/talk/{uid}` path does not exist — that guess 404ed.
         */
        fun messageListPath(page: Int = 1) = notificationListPath("message", page)

        fun messageThreadPath(uid: Long) = "/api/notification/message/with/$uid"

        const val PATH_MESSAGE_SEND = "/api/notification/message/send"
        const val PATH_MESSAGE_MARK_VIEWED_ALL = "/api/notification/message/markViewed?all=true"
    }
}
