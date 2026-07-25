package io.github.nsreader.core

/**
 * Everything we know about the shape of nodeseek.com lives here.
 *
 * NodeSeek has no public API. List and detail pages are server-rendered HTML that we scrape;
 * a handful of JSON endpoints exist for statistics and notifications but they do not cover
 * browsing. Keeping the URL vocabulary in one place means the parsers never build URLs.
 */
object NodeSeekSite {

    const val BASE_URL = "https://www.nodeseek.com"

    /** The site sits behind Cloudflare, so requests must look like a real mobile browser. */
    const val USER_AGENT =
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
    fun listPath(categorySlug: String?, page: Int): String {
        val safePage = page.coerceAtLeast(1)
        val base = if (categorySlug == null) "" else "/categories/$categorySlug"
        return if (safePage == 1) base.ifEmpty { "/" } else "$base/page-$safePage"
    }

    fun postPath(postId: Long, page: Int = 1): String = "/post-$postId-${page.coerceAtLeast(1)}"

    fun spacePath(uid: Long): String = "/space/$uid"

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

    private val POST_PATH = Regex("""/post-(\d+)(?:-(\d+))?""")
    private val SPACE_PATH = Regex("""/space/(\d+)""")

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
}
