package io.github.nsreader.core

import io.github.nsreader.model.FeedSort
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Everything we know about the shape of nodeseek.com lives here.
 *
 * NodeSeek has no public API. List and detail pages are server-rendered HTML that we scrape;
 * a handful of JSON endpoints exist for statistics and notifications but they do not cover
 * browsing. Keeping the URL vocabulary in one place means the parsers never build URLs.
 */
object NodeSeekSite {

    const val BASE_URL = "https://www.nodeseek.com"

    /**
     * Last resort only. The real UA is read off the WebView — see `resolveUserAgent`.
     *
     * Hardcoding this as *the* UA is what caused the infinite Cloudflare challenge: it contradicted
     * the UA client hints the WebView keeps sending from its actual Chromium version, and a managed
     * challenge answers a contradiction with another challenge. It survives here for the one case
     * where the WebView cannot be asked at all.
     */
    const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.6478.71 Mobile Safari/537.36"

    const val HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    val categories: List<Category> = listOf(
        Category(slug = null, title = "综合"),
        Category(slug = "daily", title = "日常"),
        Category(slug = "tech", title = "技术"),
        Category(slug = "info", title = "情报"),
        Category(slug = "review", title = "测评"),
        Category(slug = "trade", title = "交易"),
        Category(slug = "carpool", title = "拼车"),
        Category(slug = "promotion", title = "推广"),
        Category(slug = "life", title = "生活"),
        Category(slug = "dev", title = "Dev"),
        Category(slug = "photo-share", title = "贴图"),
        Category(slug = "expose", title = "曝光"),
        Category(slug = "inside", title = "内版"),
        Category(slug = "meaningless", title = "无意义"),
        Category(slug = "sandbox", title = "沙盒"),
    )

    data class Category(val slug: String?, val title: String)

    /** `null` slug means the mixed front page, which pages as `/page-2` rather than `/categories/…`. */
    fun listPath(
        categorySlug: String?,
        page: Int,
        sort: FeedSort = FeedSort.LAST_REPLY,
    ): String {
        val safePage = page.coerceAtLeast(1)
        val base = if (categorySlug == null) "" else "/categories/$categorySlug"
        val path = if (safePage == 1) base.ifEmpty { "/" } else "$base/page-$safePage"
        // The site's default order carries no parameter, so the URL stays byte-identical to what it
        // was before sorting existed — which is what keeps every cached feed key valid.
        return when (sort) {
            FeedSort.LAST_REPLY -> path
            FeedSort.POST_TIME -> "$path?sortBy=postTime"
        }
    }

    fun postPath(postId: Long, page: Int = 1): String = "/post-$postId-${page.coerceAtLeast(1)}"

    fun spacePath(uid: Long): String = "/space/$uid"

    /**
     * The site's own hash routes inside a space page.
     *
     * Worth naming because the tab a user was on is not recoverable from the path alone: `/space/12`
     * and `/space/12#/comments` are the same document, and the WebView fallbacks below only land on
     * the right tab when the fragment goes with them.
     */
    fun spaceTabPath(uid: Long, tab: String): String = "${spacePath(uid)}#/$tab"

    const val SPACE_TAB_GENERAL = "general"
    const val SPACE_TAB_DISCUSSIONS = "discussions"
    const val SPACE_TAB_COMMENTS = "comments"
    const val SPACE_TAB_COLLECTIONS = "collections"

    /** The conversation with one user, which is the only action a public space page offers. */
    fun messagePath(uid: Long): String = "/notification#/message?mode=talk&to=$uid"

    /** Follows and followers are one page with a query parameter, and only for the signed-in user. */
    fun fansPath(followers: Boolean): String = if (followers) "/fans?type=fans" else "/fans?type=follow"

    const val PROGRESS_PATH = "/progress"
    const val CREDIT_PATH = "/credit"

    fun stardustPath(uid: Long): String = "/stardust/list?member_id=$uid"

    const val RULING_PATH = "/ruling"
    const val INVITE_PATH = "/invite"
    const val LUCKY_PATH = "/lucky"
    const val PROVIDERS_PATH = "/providers"
    const val FRIENDS_PATH = "/friends"

    /**
     * Curated ("加精") threads. Server-rendered like the feed, so [listPath]'s parser applies.
     *
     * Paged as `/award/page-2`, the same scheme as the boards. `?page=` looks plausible and the server
     * even answers it — with page 1, silently, which is worse than an error (verified live 2026-07).
     */
    fun awardPath(page: Int): String {
        val safePage = page.coerceAtLeast(1)
        return if (safePage == 1) "/award" else "/award/page-$safePage"
    }

    /**
     * Account settings, one hash per group.
     *
     * Every one of these opens in the WebView rather than in a native form: they change credentials,
     * two-factor enrolment and block lists, and the site exposes no API for any of it. Guessing the
     * form fields would mean submitting credential changes we cannot verify.
     */
    fun settingPath(group: String): String = "/setting#$group"

    const val SETTING_INTRODUCTION = "introduction"
    const val SETTING_SECURITY = "security"
    const val SETTING_TWO_FACTOR = "2fa"
    const val SETTING_CONTACT = "contact"
    const val SETTING_BLOCK = "block"
    const val SETTING_PREFERENCE = "preference"
    const val SETTING_HOMEPAGE = "homepage"

    /** Accounts without an upload 404 here; [io.github.nsreader.ui.common.UserAvatar] draws the initial instead. */
    fun avatarUrl(uid: Long): String? = absoluteUrl("/avatar/$uid.png")

    const val NOTIFICATION_PATH = "/notification"

    /** The web conversation, for the "open in browser" escape hatch on the message thread. */
    fun messageThreadWebPath(uid: Long): String = "$NOTIFICATION_PATH#/message?mode=talk&to=$uid"

    const val NEW_DISCUSSION_PATH = "/new-discussion"
    const val NEW_DISCUSSION_API_PATH = "/api/content/new-discussion"

    fun postSearchPath(
        query: String,
        page: Int = 1,
        categorySlug: String? = null,
        sort: FeedSort = FeedSort.LAST_REPLY,
    ): String {
        val parameters =
            buildList {
                add("q=${query.urlEncode()}")
                if (page > 1) add("page=${page.coerceAtLeast(1)}")
                categorySlug?.takeIf(String::isNotBlank)?.let { add("category=${it.urlEncode()}") }
                if (sort == FeedSort.POST_TIME) add("sortBy=postTime")
            }
        return "/search?${parameters.joinToString("&")}"
    }

    fun userSearchPath(query: String): String = "/member?q=${query.urlEncode()}"

    fun userSearchApiPath(query: String): String = "/api/account/find/${query.urlEncode()}"

    const val SIGN_IN_PATH = "/signIn.html"

    /** Resolves site-relative URLs (`/avatar/1.png`) against the base URL; leaves absolute ones alone. */
    fun absoluteUrl(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> null
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> BASE_URL + trimmed
            else -> "$BASE_URL/$trimmed"
        }
    }

    /** Only these URLs may execute JavaScript inside the authenticated WebView. */
    fun isTrustedWebViewUrl(url: String): Boolean =
        parseWebUri(url)?.let { uri ->
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host?.lowercase() in TRUSTED_WEBVIEW_HOSTS &&
                (uri.port == -1 || uri.port == 443)
        } == true

    /** Ordinary links leave the app and are accepted only when they are normal web URLs. */
    fun isExternalWebUrl(url: String): Boolean =
        parseWebUri(url)?.let { uri ->
            uri.host != null &&
                (
                    uri.scheme.equals("https", ignoreCase = true) ||
                        uri.scheme.equals("http", ignoreCase = true)
                    )
        } == true

    private fun parseWebUri(url: String): URI? =
        runCatching { URI(url.trim()) }
            .getOrNull()
            ?.takeIf { it.isAbsolute && it.userInfo == null }

    private val POST_PATH = Regex("""/post-(\d+)(?:-(\d+))?""")
    private val SPACE_PATH = Regex("""/space/(\d+)""")
    private val TRUSTED_WEBVIEW_HOSTS = setOf("www.nodeseek.com", "nodeseek.com")

    /** Extracts the post id (and page, when present) from `/post-703863-2`. */
    fun parsePostRoute(href: String?): PostRoute? {
        val match = POST_PATH.find(href.orEmpty()) ?: return null
        val id = match.groupValues[1].toLongOrNull() ?: return null
        val page = match.groupValues[2].toIntOrNull() ?: 1
        return PostRoute(id, page)
    }

    fun parseUid(href: String?): Long? =
        SPACE_PATH.find(href.orEmpty())?.groupValues?.get(1)?.toLongOrNull()

    data class PostRoute(val postId: Long, val page: Int)

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
