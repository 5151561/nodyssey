package io.github.nodyssey.core.html

import io.github.plaza.core.net.CLOUDFLARE_CHALLENGE_MARKERS

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

    /**
     * The red-boxed 只读 label on an announcement row. Same meaning as the lock icon
     * (additions.md §1.4), so the parser folds it into `isLocked` — the app draws one lock state.
     */
    const val LIST_READ_ONLY = "div.post-title span:matchesOwn(^\\s*只读\\s*$)"

    /**
     * What the server puts on a row whose author this account has blocked.
     *
     * Read off the site's own bundle on 2026-08-03: its 临时显示Block内容 menu item is nothing but
     * `document.querySelectorAll(".blocked-post")` with the class removed, and the stylesheet carries
     * `.blocked-post,.blocked-comment{display:none!important}`. So the content is *sent*, marked, and
     * hidden by CSS — a scraper receives it like any other row unless it looks for the class.
     */
    const val BLOCKED_POST_CLASS = "blocked-post"
    const val BLOCKED_COMMENT_CLASS = "blocked-comment"
    const val LIST_AVATAR = "img.avatar-normal, img[src*=/avatar/]"
    const val LIST_AUTHOR = "span.info-author a[href]"
    const val LIST_VIEWS = "span.info-views span"
    const val LIST_COMMENTS = "span.info-comments-count span"
    const val LIST_LAST_ACTIVE = "a.info-last-comment-time time"

    /** The class moved once already, so fall back to any link into a board. */
    const val LIST_CATEGORY = "a.post-category, div.post-info a[href*=/categories/]"
    const val LIST_PAGER_NEXT = "a.pager-next[href]"
    const val LIST_PAGER_POSITIONS = "[aria-label=pagination] .pager-pos"

    /** The server-rendered total in the 首页 sidebar's 用户数目 panel. */
    const val COMMUNITY_MEMBER_COUNT =
        "div.nsk-panel:has(h4:containsOwn(用户数目)) > div:containsOwn(目前论坛共有)"

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

    /** The header strip holding the time — and, on edited floors, the `edited Xmin ago` marker. */
    const val CONTENT_INFO = "div.content-info"
    const val CONTENT_CATEGORY = "span.content-category a"
    const val CONTENT_FLOOR = "a.floor-link"
    const val CONTENT_ARTICLE = "article.post-content"
    const val CONTENT_SIGNATURE = "div.signature"

    /**
     * The `nsapp://vote?id=2871` marker a vote leaves in the body.
     *
     * The scheme is on `data-href`, not `href` — the anchor's own `href` is `javascript://void(0)`,
     * because the site's script intercepts the click. Matching on `href` finds nothing.
     */
    const val VOTE_PLACEHOLDER_ATTR = "data-href"
    const val VOTE_PLACEHOLDER_SCHEME = "nsapp://vote"
    const val VOTE_PLACEHOLDER = "a[$VOTE_PLACEHOLDER_ATTR^=$VOTE_PLACEHOLDER_SCHEME]"

    // --- Page-level state ---------------------------------------------------

    /**
     * Markers that prove we received a real NodeSeek page rather than an interstitial.
     *
     * The bootstrap is on this list because Cloudflare inlines its `/cdn-cgi/challenge-platform/`
     * script into *every* page it serves us, challenge or not — measured on device 2026-08-02, where
     * the home feed and `/setting` came back 200 with `cf-mitigated` empty and both carrying that
     * script. The four content markers are what saved the forum pages. `/setting` has none of them:
     * it is server-rendered without `nsk-body` and its account fields (email, `telegram_id`) live
     * only in the bootstrap, so 联系方式 read every good response as a challenge until this line
     * existed. An interstitial is served *instead of* the site and so never carries the bootstrap.
     */
    val USABLE_PAGE_MARKERS = listOf(
        "id=\"nsk-body\"",
        "class=\"post-list\"",
        "class=\"nsk-post\"",
        "class=\"post-content\"",
        "class=\"comments\"",
        "id=\"temp-script\"",
    )

    val LOGIN_REQUIRED_MARKERS = listOf("需要注册用户才能查看", "权限不足")

    /**
     * Cloudflare's markup, not NodeSeek's, so the list itself lives in `:core`.
     *
     * Re-exported under this name because the JSON callers below check a response body for an
     * interstitial by hand, and they read better checking one file's worth of markers rather than
     * reaching across modules for half of them.
     */
    val CLOUDFLARE_MARKERS = CLOUDFLARE_CHALLENGE_MARKERS

    /**
     * NodeSeek's own throttle sentence, served as a JSON body on an HTML route.
     *
     * Captured live from `/search?q=…` on 2026-08-01: two requests inside two seconds and the second
     * comes back `429 {"success":false,"message":"每隔2秒可以操作一次"}`. The status alone would be
     * enough today, but the sentence is what makes the classification legible if the status ever
     * softens to a 200.
     */
    val RATE_LIMIT_MARKERS = listOf("每隔2秒可以操作一次")
}
