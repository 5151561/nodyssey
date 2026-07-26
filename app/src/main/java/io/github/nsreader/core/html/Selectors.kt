package io.github.nsreader.core.html

/**
 * Every CSS selector that depends on NodeSeek's markup. When the site changes its templates this
 * is the only file that should need editing — parsers must not inline selector strings.
 */
object Selectors {

    // --- Topic list ---------------------------------------------------------
    const val LIST_ITEM = "ul.post-list > li.post-list-item"
    const val LIST_TITLE_LINK = "div.post-title > a[href]"
    const val LIST_PINNED = "div.post-title use[href=#pin]"
    const val LIST_LOCKED = "div.post-title use[href=#lock]"
    const val LIST_AVATAR = "img.avatar-normal, img[src*=/avatar/]"
    const val LIST_AUTHOR = "span.info-author a[href]"
    const val LIST_VIEWS = "span.info-views span"
    const val LIST_COMMENTS = "span.info-comments-count span"
    const val LIST_LAST_ACTIVE = "a.info-last-comment-time time"

    /** The class moved once already, so fall back to any link into a board. */
    const val LIST_CATEGORY = "a.post-category, div.post-info a[href*=/categories/]"
    const val LIST_PAGER_NEXT = "a.pager-next[href]"
    const val LIST_PAGER_POSITIONS = "[aria-label=pagination] .pager-pos"

    // --- Post detail --------------------------------------------------------
    const val DETAIL_TITLE = "div.post-title a.post-title-link"
    const val DETAIL_TITLE_FALLBACK = "div.post-title h1"
    const val DETAIL_BODY_ITEM = "div.nsk-post > div.content-item"
    const val DETAIL_COMMENTS = "ul.comments > li.content-item"
    const val DETAIL_PAGER = "div.nsk-pager"
    const val DETAIL_PAGER_POSITIONS = "a.pager-pos, span.pager-pos"
    const val DETAIL_PAGER_NEXT = "a.pager-next[href]"

    // --- One post body or comment ------------------------------------------
    const val CONTENT_AVATAR = "div.avatar-wrapper img"
    const val CONTENT_AUTHOR = "a.author-name"
    const val CONTENT_POSTER_BADGE = "div.author-info .is-poster"
    const val CONTENT_BADGES = "div.author-info .nsk-badge, div.author-info .role-tag"
    const val CONTENT_CREATED_AT = "span.date-created time"
    const val CONTENT_CATEGORY = "span.content-category a"
    const val CONTENT_FLOOR = "a.floor-link"
    const val CONTENT_ARTICLE = "article.post-content"

    // --- Page-level state ---------------------------------------------------

    /** Markers that prove we received a real NodeSeek page rather than an interstitial. */
    val USABLE_PAGE_MARKERS = listOf(
        "id=\"nsk-body\"",
        "class=\"post-list\"",
        "class=\"nsk-post\"",
        "class=\"post-content\"",
        "class=\"comments\"",
    )

    val LOGIN_REQUIRED_MARKERS = listOf("需要注册用户才能查看", "权限不足")

    val CLOUDFLARE_MARKERS = listOf(
        "/cdn-cgi/challenge-platform/",
        "cf-browser-verification",
        "Just a moment...",
        "Checking your browser before accessing",
    )
}
