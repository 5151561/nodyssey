package io.github.nodyssey.core.net

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.Selectors
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
 * Endpoints under `/api/notification`, `/api/statistics` and `/api/admin` answer **500 when
 * unauthenticated** rather than 401. [NodeSeekJsonClient] maps that quirk to
 * [SiteError.LoginRequired] itself, so callers see the same error a 401 would have produced.
 */
interface JsonSource {
    suspend fun getJson(path: String, referer: String = NodeSeekSite.BASE_URL + "/"): String

    /**
     * POST to a JSON endpoint with the same headers and challenge classification as [getJson].
     *
     * Unlike [getJson] the answer comes back with its status code instead of a thrown [SiteError.Http]:
     * NodeSeek's write endpoints put meaningful JSON bodies on non-2xx statuses (a repeat sign-in is
     * an HTTP 500 whose body carries the sentence to show), so status-versus-body is the caller's
     * contract. Transport failures and Cloudflare interception still throw.
     */
    suspend fun postJson(path: String, referer: String = NodeSeekSite.BASE_URL + "/"): JsonPostResponse =
        throw UnsupportedOperationException("This JsonSource does not support POST")
}

/**
 * Writes, kept apart from [JsonSource] so a repository that only reads cannot accidentally post —
 * and so the many read-only test doubles stay as small as they are.
 */
interface JsonWriteSource {
    /**
     * Sends a JSON body and returns the JSON answer.
     *
     * Unlike [JsonSource.getJson] this maps 401/403 to [SiteError.LoginRequired]: a write is the
     * one moment where a session that quietly expired has to be told apart from a server fault,
     * because the recovery is "sign in and send again" rather than "retry".
     */
    suspend fun postJson(
        path: String,
        body: String,
        referer: String = NodeSeekSite.BASE_URL + "/",
    ): String

    /**
     * A write whose status code is the caller's to read, for the endpoints that put the meaning in
     * the body regardless of what they answer with.
     *
     * The vote family is the reason this exists: refusing a lock answers 403, a bad option id answers
     * 422, and a delete without moderator rights answers 500 — each with the sentence to show in
     * `{"success":false,"message":…}`. Through [postJson] the first of those would surface as
     * [SiteError.LoginRequired] and tell a signed-in reader to sign in.
     *
     * [method] is a parameter rather than there being one function per verb because the vote writes
     * are POST and DELETE with identical handling — `DELETE /api/vote/info/{id}` carries a body, which
     * is unusual but is what the site does. Two identical implementations differing only in a string
     * would explain less than this does.
     *
     * Defaults to throwing, like [JsonSource.postJson], so the read-only test doubles stay small.
     */
    suspend fun sendJson(
        method: String,
        path: String,
        body: String,
        referer: String = NodeSeekSite.BASE_URL + "/",
    ): JsonPostResponse = throw UnsupportedOperationException("This JsonWriteSource does not support $method")
}

/** Both halves, for the repositories that read a list and then add to it. */
interface JsonApi :
    JsonSource,
    JsonWriteSource

/** Multipart writes used by the setting page's image cropper. */
interface MultipartWriteSource {
    suspend fun postMultipart(
        path: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMimeType: String,
        headers: Map<String, String> = emptyMap(),
        referer: String = NodeSeekSite.BASE_URL + "/",
    ): String
}

class NodeSeekJsonClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : JsonApi,
    MultipartWriteSource {

    override suspend fun getJson(path: String, referer: String): String =
        withContext(dispatchers.io) {
            val response = execute(xhrRequest(path, referer).build())

            response.use {
                val body = it.body.string()
                throwIfChallenge(it.header("cf-mitigated"), body)
                // Session-scoped endpoints answer 500, not 401, when the cookie is missing or stale.
                // "服务器错误" with a retry button would hide the one action that fixes it: signing in.
                if (it.code == 500 && isSessionScoped(path)) {
                    throw SiteException(SiteError.LoginRequired)
                }
                if (!it.isSuccessful) throw SiteException(SiteError.Http(it.code))
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
                val body = it.body.string()
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
            throw SiteException(SiteError.Network, e)
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
        if (isChallenge) throw SiteException(SiteError.Cloudflare)
    }

    /** The endpoint families that only answer for a signed-in session — see the interface KDoc. */
    private fun isSessionScoped(path: String): Boolean =
        path.startsWith("/api/notification") ||
            path.startsWith("/api/statistics") ||
            // 管理记录 is public reading behind a private endpoint: signed out it answers 500 with
            // `{"message":"USER NOT FOUND"}`, so without this the moderation log would offer a retry
            // button for a state no retry clears.
            path.startsWith("/api/admin")

    override suspend fun postJson(path: String, body: String, referer: String): String =
        withContext(dispatchers.io) {
            val request = xhrRequest(path, referer)
                .header("Origin", NodeSeekSite.BASE_URL)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = execute(request)

            response.use {
                val payload = it.body.string()
                throwIfChallenge(it.header("cf-mitigated"), payload)
                if (it.code == 401 || it.code == 403) {
                    throw SiteException(SiteError.LoginRequired)
                }
                if (!it.isSuccessful) throw SiteException(SiteError.Http(it.code))
                payload
            }
        }

    override suspend fun sendJson(
        method: String,
        path: String,
        body: String,
        referer: String,
    ): JsonPostResponse =
        withContext(dispatchers.io) {
            val request =
                xhrRequest(path, referer)
                    .header("Origin", NodeSeekSite.BASE_URL)
                    .method(method, body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            val response = execute(request)

            response.use {
                val payload = it.body.string()
                // Still thrown, and still before the status: a challenge is not an answer from the
                // site, so there is no site sentence for the caller to read.
                throwIfChallenge(it.header("cf-mitigated"), payload)
                JsonPostResponse(code = it.code, body = payload)
            }
        }

    override suspend fun postMultipart(
        path: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMimeType: String,
        headers: Map<String, String>,
        referer: String,
    ): String =
        withContext(dispatchers.io) {
            val multipart =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .apply {
                        fields.forEach { (name, value) -> addFormDataPart(name, value) }
                    }.addFormDataPart(
                        fileField,
                        fileName,
                        fileBytes.toRequestBody(fileMimeType.toMediaType()),
                    ).build()
            val request =
                xhrRequest(path, referer)
                    .header("Origin", NodeSeekSite.BASE_URL)
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .post(multipart)
                    .build()
            val response = execute(request)

            response.use {
                val payload = it.body.string()
                throwIfChallenge(it.header("cf-mitigated"), payload)
                if (it.code == 401 || it.code == 403) {
                    throw SiteException(SiteError.LoginRequired)
                }
                if (!it.isSuccessful) throw SiteException(SiteError.Http(it.code))
                payload
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val PATH_CATEGORIES = "/api/content/list-categories"
        const val PATH_UNREAD_COUNT = "/api/notification/unread-count"

        fun notificationListPath(type: String, page: Int = 1) =
            "/api/notification/$type/list?page=$page"

        /**
         * The endpoint drops `readme` from the payload unless the flag asks for it, so the space
         * page has to opt in the way the site's own space page does — without it every profile
         * renders the "没有找到readme" empty state.
         */
        fun accountInfoPath(uid: Long) = "/api/account/getInfo/$uid?readme=1"

        /** The setting page requests these flags to include the editable Markdown fields. */
        fun accountSettingsInfoPath(uid: Long) =
            "/api/account/getInfo/$uid?readme=1&signature=1&phone=1"

        const val PATH_ACCOUNT_INTRODUCTION = "/api/account/introduction"
        const val PATH_AVATAR_UPLOAD = "/api/avatar/upload"
        const val PATH_ACCOUNT_CHANGE_PASSWORD = "/api/account/changePassword"
        const val PATH_ACCOUNT_OTP_STATUS = "/api/account/otp-status"

        /** Both halves of TOTP enrolment; the body's `action` is `create` or `remove`. */
        const val PATH_ACCOUNT_OTP = "/api/account/otp"
        const val PATH_ACCOUNT_TELEGRAM = "/api/account/telegram"
        const val PATH_ACCOUNT_UNBIND_TELEGRAM = "/api/account/unbind-telegram"
        const val PATH_PREFERENCE_LIST = "/api/preference/list"
        const val PATH_PREFERENCE_SET = "/api/preference/set"
        const val PATH_HOMEPAGE = "/api/homepage"
        const val PATH_BLOCK_LIST = "/api/block-list/list"

        /** Takes `block_member_name` — the site blocks by username, not by uid. */
        const val PATH_BLOCK_ADD = "/api/block-list/add"
        const val PATH_BLOCK_DELETE = "/api/block-list/del"

        fun discussionListPath(uid: Long, page: Int = 1) =
            "/api/content/list-discussions?uid=$uid&page=${page.coerceAtLeast(1)}"

        fun commentListPath(uid: Long, page: Int = 1) =
            "/api/content/list-comments?uid=$uid&page=${page.coerceAtLeast(1)}"

        /** Collections belong to the session, not to a uid: the site offers no one else's. */
        fun collectionListPath(page: Int = 1) =
            "/api/statistics/list-collection?page=${page.coerceAtLeast(1)}"

        /**
         * Collecting and un-collecting a thread: `{"postId":857694,"action":"add"|"remove"}`, answered
         * `{"success":true,"message":"added"|"removed","postCollectionCount":N,"userCollectionCount":M}`.
         *
         * Sits under `/api/statistics` alongside the three reactions, but unlike them it is
         * reversible — which is why it is a toggle here rather than another
         * [NodeSeekSite.reactionApiPath] action.
         */
        const val PATH_COLLECTION = "/api/statistics/collection"

        /*
         * 投票. Captured from the live site on 2026-08-06.
         *
         * Every one of these needs the `x-dynamic-sign` header — see [DynamicSignInterceptor] —
         * without which they answer 403 regardless of the session. Reading works signed out.
         *
         * `info` is one path with three verbs: GET reads a vote, POST (with no id) creates one,
         * DELETE (with an id, and with a body) removes it. That is the site's shape, not our tidying.
         */

        /** `{"success":true,"vote":{id,title,uid,isPublic,locked,multiple,items:[…]}}`. */
        fun voteInfoPath(voteId: Long) = "/api/vote/info/$voteId"

        /** Create: `{"title","multiple","isPublic","items":[…]}` → `{"success":true,"vote":{"id":N}}`. */
        const val PATH_VOTE_CREATE = "/api/vote/info"

        /** Cast: `{"ids":[13201]}` — an array even for a single-choice vote. */
        const val PATH_VOTE_SUBMIT = "/api/vote/voteforitem"

        /**
         * Lock or unlock: `{"locked":true}`.
         *
         * Locking is the vote's owner's to do; *un*locking is a moderator's. Somebody else's vote
         * answers `403 {"success":false,"message":"You are not the owner of vote"}`.
         */
        fun voteLockPath(voteId: Long) = "/api/vote/lock/$voteId"

        /**
         * One option's voters as bare uids, ten to a page. Public votes only.
         *
         * There is no total in the answer — the site derives the page count from the option's own
         * `count`, and so do we.
         */
        fun voterOfItemPath(
            itemId: Long,
            page: Int = 1,
        ) = "/api/vote/voter-of-item?id=$itemId&page=${page.coerceAtLeast(1)}"

        const val VOTER_PAGE_SIZE = 10

        /** `random` is the sign-in mode's wire value: "true" gambles, "false" takes a flat five. */
        fun attendancePath(random: String) = "/api/attendance?random=$random"

        fun attendanceBoardPath(page: Int = 1) =
            "/api/attendance/board?page=${page.coerceAtLeast(1)}"

        /**
         * Session-scoped chicken ledger; rows are `[change, balance, reason, createdAt]`.
         *
         * Twenty rows a page, and the payload's `total` is a row count rather than a page count —
         * the site's own page bar is `min(100, ceil(total / 20))`, so page 101 is unreachable there
         * too. Both numbers live in [CREDIT_PAGE_SIZE] and [CREDIT_MAX_PAGES].
         */
        fun creditLedgerPath(page: Int = 1) = "/api/account/credit/page-${page.coerceAtLeast(1)}"

        const val CREDIT_PAGE_SIZE = 20

        /** Not our cap: the site's own pager stops here, so asking past it buys nothing. */
        const val CREDIT_MAX_PAGES = 100

        /**
         * The stardust ledger, paged by cursor rather than by page number.
         *
         * The server rejects any parameter outside its whitelist with **HTTP 422** rather than
         * ignoring it (`"cursor" is not allowed` — the response's own `cursor` field is *not* a
         * query parameter). The accepted names are `id`, `member_id`, `peer_id`, `comment_id`,
         * `type`, `after_id`, `after_time`, `before_id`, `before_time`, `count` and `ref_id`;
         * [beforeId] takes the previous page's `cursor`, and the bound is exclusive.
         */
        fun stardustListPath(
            memberId: Long,
            beforeId: Long? = null,
            count: Int = STARDUST_PAGE_SIZE,
        ): String {
            val query = StringBuilder("/api/stardust/list?member_id=$memberId&count=${count.coerceAtLeast(1)}")
            beforeId?.let { query.append("&before_id=$it") }
            return query.toString()
        }

        const val STARDUST_PAGE_SIZE = 20

        /**
         * Everything paid against one 收款码: the same ledger, narrowed to a payee and their `ref_id`.
         *
         * [peerId] narrows it further to one payer, which is how a card knows whether *you* have
         * already paid — a non-empty answer is a yes.
         *
         * Deliberately not narrowed by `type`, though the whitelist above would allow it. The site's
         * own card does not, and every `upvote` row carries `ref_id` 10, so a code whose Ref ID is 10
         * counts other people's likes as payments — on the web too. Filtering here would make this
         * app disagree with the page the payee is looking at, about money, and being quietly right is
         * worth less than the two of them matching.
         *
         * No cursor: a card shows a total, and one page of it is what the site shows. [count] is the
         * one lever, and it is already five times the site's own.
         */
        fun stardustReceiptsPath(
            memberId: Long,
            refId: Long,
            peerId: Long? = null,
            count: Int = STARDUST_RECEIPT_LIMIT,
        ): String {
            val query =
                StringBuilder(
                    "/api/stardust/list?member_id=$memberId&ref_id=$refId&count=${count.coerceAtLeast(1)}",
                )
            peerId?.let { query.append("&peer_id=$it") }
            return query.toString()
        }

        /**
         * How many payments a card will count.
         *
         * The site asks for its default page and reports `records.length`, so a popular code
         * under-reports there. Asking for more is free and the answer is a count either way.
         */
        const val STARDUST_RECEIPT_LIMIT = 100

        /*
         * 星辰转账: the two writes behind the site's own transfer layer.
         *
         * `payment-prepare` takes `{receiver_id, origin}` and answers `{success, receiver_name}` —
         * it is the name echo the site shows on its confirmation step, not a token the send depends
         * on; `send` takes `{member_id, diff, ref_id}` on its own and there is no Turnstile in front
         * of either. `member_id` on the send is the **receiver**: the sender is whoever the cookie
         * says, and the site never puts its own id in the body.
         */
        const val PATH_STARDUST_PREPARE = "/api/stardust/payment-prepare"
        const val PATH_STARDUST_SEND = "/api/stardust/send"

        /** `?all=true` with an empty body. */
        fun markAllViewedPath(type: String) = "/api/notification/$type/markViewed?all=true"

        /**
         * The per-row form of the same endpoint: no query, and a JSON body whose one field is named
         * after the group — see [io.github.nodyssey.data.NotificationCategory.viewedField].
         */
        fun markViewedPath(type: String) = "/api/notification/$type/markViewed"

        /*
         * Direct messages.
         *
         * Read out of the site's own `notification.js` on a signed-in device (2026-07-26) rather
         * than inferred: `list` returns one row per conversation holding only its latest message,
         * `with/{uid}` returns the full history plus a `talkTo` header, and `send` is a POST whose
         * recipient field is camel-cased `receiverUid` while every other field on this API is
         * snake_case. A `message/talk/{uid}` path does not exist — that guess 404ed.
         */
        // Callers currently read page 1 only, so a very long inbox is truncated; the parameter is
        // here so paging is a call-site change rather than a new endpoint.
        fun messageListPath(page: Int = 1) = notificationListPath("message", page)

        fun messageThreadPath(uid: Long) = "/api/notification/message/with/$uid"

        const val PATH_MESSAGE_SEND = "/api/notification/message/send"
        const val PATH_MESSAGE_MARK_VIEWED_ALL = "/api/notification/message/markViewed?all=true"
        const val PATH_MESSAGE_MARK_VIEWED = "/api/notification/message/markViewed"

        /*
         * 关注 / 粉丝, read out of the site's own `fans` bundle on 2026-08-02.
         *
         * The list kind is the **last path segment**, not a query parameter — the web route's
         * `?type=fans` becomes `/api/fans/fans` here, and `?type=follow` becomes `/api/fans/follow`.
         * Neither takes a page: the site fetches the whole list in one call and pages nothing, so a
         * `page` parameter on the repository would have been a knob wired to nothing.
         *
         * A signed-out GET answers 200 with an empty `memberList` rather than 401 — see
         * [io.github.nodyssey.data.NetworkFollowRepository] for why that has to be caught before the
         * request rather than after it.
         */
        fun fansListPath(followers: Boolean): String =
            if (followers) "/api/fans/fans" else "/api/fans/follow"

        /** Both writes take `{"followed_member_id": <uid>}` and answer `{success, message}`. */
        const val PATH_FANS_ADD = "/api/fans/add"
        const val PATH_FANS_DEL = "/api/fans/del"

        /*
         * 管理记录, read out of the site's own `ruling` bundle on 2026-08-02.
         *
         * `admin` names the endpoint, not the caller: any signed-in account reads the log, and the
         * page number is a path segment here even though the web route pages by hash — see
         * [io.github.nodyssey.core.NodeSeekSite.rulingPath].
         *
         * Both bounds below are the server's, answered as HTTP 200 with `success:false`: page 0 is
         * "wrong page number" and page 101 is "max page is 100". The cap is real and low enough to
         * matter — 30 212 decisions is 1 511 pages of twenty, of which the site serves the first 100.
         */
        fun rulingPagePath(page: Int): String = "/api/admin/ruling/page-${page.coerceAtLeast(1)}"

        const val RULING_PAGE_SIZE = 20
        const val RULING_MAX_PAGES = 100
    }
}
