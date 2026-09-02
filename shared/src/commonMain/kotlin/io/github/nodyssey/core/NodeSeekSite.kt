package io.github.nodyssey.core

import io.github.nodyssey.core.html.Selectors
import io.github.nodyssey.model.FeedSort
import io.github.plaza.core.net.PageMarkers
import io.github.plaza.core.net.SiteConfig
import io.github.plaza.core.net.WebUrl

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

    /**
     * What `:core` is told about this site, and the only channel it has: nothing in that module may
     * import from this one.
     *
     * Assembled here rather than in the DI container so the values sit beside the notes explaining
     * them — the fallback UA's warning is three lines up, and the marker lists carry the measurements
     * that produced them over in [Selectors].
     */
    val CONFIG =
        SiteConfig(
            baseUrl = BASE_URL,
            fallbackUserAgent = FALLBACK_USER_AGENT,
            htmlAccept = HTML_ACCEPT,
            // The site sets `session`; the JWT-style `token` shows up on some deployments.
            sessionCookieNames = listOf("session", "token"),
            markers =
            PageMarkers(
                usablePage = Selectors.USABLE_PAGE_MARKERS,
                loginRequired = Selectors.LOGIN_REQUIRED_MARKERS,
                levelRequired = Selectors.LEVEL_REQUIRED_MARKERS,
                privatePost = Selectors.PRIVATE_POST_MARKERS,
                rateLimit = Selectors.RATE_LIMIT_MARKERS,
            ),
        )

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

    /**
     * Floors per comment page.
     *
     * Needed because the site names a floor and leaves the page implicit: a reply notification carries
     * `floor_id` and nothing else, and `a.floor-link` is a bare `#4` anchor. Without this constant the
     * only way to reach floor #127 is to walk every page until it turns up.
     *
     * Ten is what the committed `post-703863-1` capture holds — the opening post plus #1–#10, with the
     * pager offering four pages — and the value confirmed against the live site.
     */
    const val COMMENTS_PER_PAGE = 10

    /**
     * The page [floor] is rendered on. `#0` is the opening post, which the site puts on page 1 with
     * floors #1–#10.
     */
    fun pageOfFloor(floor: Int): Int = if (floor <= 0) 1 else (floor - 1) / COMMENTS_PER_PAGE + 1

    /** `"#127"` → 127. Null for a floor the site did not number, which is nothing to jump to. */
    fun parseFloorNumber(floor: String?): Int? = floor?.trim()?.removePrefix("#")?.trim()?.toIntOrNull()

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

    /** Follows and followers are one page with a query parameter, and only for the signed-in user. */
    fun fansPath(followers: Boolean): String = if (followers) "/fans?type=fans" else "/fans?type=follow"

    const val PROGRESS_PATH = "/progress"
    const val CREDIT_PATH = "/credit"

    fun stardustPath(uid: Long): String = "/stardust/list?member_id=$uid"

    const val RULING_PATH = "/ruling"

    /**
     * 管理记录, page [page].
     *
     * The page number is a **hash route**, not a path segment: the table is a Vue app whose own
     * paginator links `#/p-2`, and `/ruling/page-2` — the scheme every server-rendered list here uses
     * — is a 404 (verified live 2026-08-02). Worth a function rather than a constant because both the
     * referer and the "open in browser" fallback should land on the page the user was reading.
     */
    fun rulingPath(page: Int = 1): String = if (page <= 1) RULING_PATH else "$RULING_PATH#/p-$page"
    const val INVITE_PATH = "/invite"
    const val LUCKY_PATH = "/lucky"
    const val PROVIDERS_PATH = "/providers"
    const val FRIENDS_PATH = "/friends"
    const val ABOUT_PATH = "/about"
    const val TERMS_OF_SERVICE_PATH = "/termsofservice"

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
     * Account settings, one hash per group — the site routes its own tabs off `location.hash`.
     *
     * Most groups are native screens now. Two are not, and cannot be: sending an email verification
     * code needs a Cloudflare Turnstile token, and binding Telegram runs telegram.org's login widget.
     * Both need a browser, so [SETTING_CONTACT] is where the app hands those two off.
     */
    fun settingPath(group: String): String = "/setting#$group"

    const val SETTING_INTRODUCTION = "introduction"
    const val SETTING_SECURITY = "security"
    const val SETTING_TWO_FACTOR = "2fa"
    const val SETTING_CONTACT = "contact"
    const val SETTING_BLOCK = "block"
    const val SETTING_PREFERENCE = "preference"
    const val SETTING_HOMEPAGE = "homepage"

    /** Accounts without an upload 404 here; `UserAvatar` draws the initial instead. */
    fun avatarUrl(uid: Long): String? = absoluteUrl("/avatar/$uid.png")

    /**
     * Whether [url] names an account's picture rather than a piece of content.
     *
     * The distinction the image cache is built on: an attachment's address changes when its bytes
     * do, and one of these does not — the account keeps it for as long as the account exists, and
     * uploading a new picture changes nothing about it.
     *
     * Matched against this site's own base URL rather than by path alone, so an address that merely
     * happens to have `/avatar/` in it somewhere else is not one of ours. Both producers of these
     * pass through [absoluteUrl] first — [avatarUrl] and the `src` the list and thread parsers read
     * — so by the time anything asks, a site-relative address already carries the base.
     */
    fun isAvatarUrl(url: String): Boolean = url.startsWith("$BASE_URL/avatar/")

    /**
     * Where the site keeps its own stickers, e.g. `ac/01.png`.
     *
     * Post bodies already point here — the parser recognises an inline sticker by this very path
     * ([io.github.nodyssey.core.html.RichContentParser]) — so the emoji panel using it too means one
     * cached copy serves both, instead of the panel shipping a second copy inside the APK.
     */
    fun stickerUrl(
        group: String,
        code: String,
        extension: String,
    ): String = "$BASE_URL/static/image/sticker/$group/$code.$extension"

    const val NOTIFICATION_PATH = "/notification"

    /** The web conversation, for the "open in browser" escape hatch on the message thread. */
    fun messageThreadWebPath(uid: Long): String = "$NOTIFICATION_PATH#/message?mode=talk&to=$uid"

    const val NEW_DISCUSSION_PATH = "/new-discussion"
    const val NEW_DISCUSSION_API_PATH = "/api/content/new-discussion"

    /**
     * Posting a reply. Captured from a signed-in session on the sandbox thread (2026-07-28) rather
     * than guessed: the editor sends `{content, mode:"new-comment", postId}` and answers
     * `{"success":true,"redirect":"/post-841108-1","redirectHash":"#3"}` — the floor number only
     * exists in that hash, which is why [parseFloorHash] is here and not in the parser package.
     */
    const val NEW_COMMENT_API_PATH = "/api/content/new-comment"

    const val NEW_COMMENT_MODE = "new-comment"

    /**
     * Rewriting what is already posted. Two endpoints because the site has two: the opening post
     * carries a title and a 阅读权限 and is addressed by `postId`, a reply carries neither and is
     * addressed by `commentId`.
     *
     * Read off the site's own editor bundle (`markdownEditor-*.js`, 2026-08-16) rather than guessed:
     * it builds one payload with a `mode` discriminator and adds `{title, postId, rank}` for
     * `edit-discussion` and `{commentId}` for `edit-comment`. Both answer in the same
     * `{success, message}` shape the two `new-*` endpoints do, and neither returns the new body.
     *
     * The site's client shows 编辑 on the condition `logined && isMe` alone — there is no time
     * window and no edit count in it — so the app offers it on the same condition and lets the
     * server refuse anything it refuses.
     */
    const val EDIT_DISCUSSION_API_PATH = "/api/content/edit-discussion"

    const val EDIT_DISCUSSION_MODE = "edit-discussion"

    const val EDIT_COMMENT_API_PATH = "/api/content/edit-comment"

    const val EDIT_COMMENT_MODE = "edit-comment"

    /** `"#3"` → `3`. Anything else is a shape we did not expect, and the caller falls back to null. */
    fun parseFloorHash(hash: String?): Int? = hash?.trimStart('#')?.trim()?.toIntOrNull()

    /**
     * Reacting to a floor. All three take `{commentId, action:"add"}` and answer
     * `{success, current, coin, message}`, where `current` is that one tally's new value.
     *
     * The names do not mean what they look like, and the mapping is the whole reason this is spelled
     * out here: the site's `like` is **加鸡腿**, which costs the reader a chicken leg, and its
     * `dislike` is **反对**, which costs two. The free one that pays the author in stardust is
     * `upvote`. Wiring a thumb-up icon to `like` would quietly spend the reader's currency.
     *
     * There is no remove for any of them; see [io.github.nodyssey.model.PostReactions].
     *
     * [action] is [io.github.nodyssey.model.ReactionAction.apiAction], which is where the wire words
     * are defined — they are not repeated here.
     */
    fun reactionApiPath(action: String): String = "/api/statistics/$action"

    /**
     * Today's four allowances, all of them, in one request.
     *
     * Verified against the live site on 2026-08-02 by reading `/static/js/progress.*.js` — the bundle
     * behind `/progress`, which is a client-rendered page and was long assumed to have no endpoint
     * behind it. It has this one, and **unscoped it answers with every field at once**:
     * `{success, postBonusCount, maxPostBonusCount, commentBonusCount, maxCommentBonusCount,
     * freeLikeUsed, maxFreeLike}`.
     *
     * Units differ between the two bonus pairs, and that is the trap: the site multiplies the *post*
     * pair by five to draw its bar (a post pays five chicken legs, `maxPostBonusCount` is 4 → the
     * `0 / 20` on the design), while the comment pair is already counted in chicken legs. Rendering
     * both raw would show today's posting allowance as `0 / 4`.
     *
     * The fourth allowance — 今日签到 — is not here; the same page reads it from
     * [io.github.nodyssey.core.net.NodeSeekJsonClient.attendanceBoardPath]'s `record`.
     */
    const val PROGRESS_TODAY_API_PATH = "/api/progress/today"

    /**
     * The 免费投喂 half of [PROGRESS_TODAY_API_PATH], `{maxFreeLike, freeLikeUsed}`.
     *
     * Kept scoped for the reaction path: a reader about to spend a chicken leg is waiting on this
     * lookup, and there is no reason to make them wait for four allowances to answer one question.
     */
    const val FREE_LIKE_QUOTA_API_PATH = "$PROGRESS_TODAY_API_PATH?scope=freelike"

    /**
     * The chicken-leg span of one level: `[rank² × 100, (rank+1)² × 100)`.
     *
     * The same `/progress` bundle draws its level bar from this, and it is a published formula rather
     * than a guessed curve — the 400 that used to be treated as the only known threshold is just this
     * at `rank = 1`. Lv2 runs 400–900, Lv3 900–1600, Lv4 1600–2500.
     *
     * `rank` is clamped at 5 exactly as the site clamps it (`Math.min(user.rank, 5)`), which means a
     * higher-ranked account keeps being drawn against the Lv5 → Lv6 span with a full bar. That is the
     * site's own behaviour and not an extrapolation; nothing is published beyond it.
     */
    fun levelChickenSpan(rank: Int): LevelSpan {
        val clamped = rank.coerceIn(1, LEVEL_BAR_MAX_RANK)
        return LevelSpan(
            barRank = clamped,
            floor = clamped * clamped * LEVEL_CHICKEN_UNIT,
            next = (clamped + 1) * (clamped + 1) * LEVEL_CHICKEN_UNIT,
        )
    }

    private const val LEVEL_CHICKEN_UNIT = 100

    /** `Math.min(user.rank, 5)` on the site: the bar stops advancing past Lv5. */
    private const val LEVEL_BAR_MAX_RANK = 5

    /**
     * Post search. The same server-rendered list the boards serve, at a different route.
     *
     * Verified against the live site on 2026-08-01, because the app used to treat this route as if
     * it were something special:
     * - the response is one plain document — no XHR, no search API — carrying `#nsk-frame`,
     *   `.post-list-item` **fifty to a page** and the ordinary `pager-next`, so [listPath]'s parser
     *   reads it unchanged;
     * - `category` is honoured **server-side**, and exactly one is accepted;
     * - `sortBy=postTime` is honoured; its absence is the site's own relevance-ish ordering;
     * - asking for a page past the end returns zero rows **but still renders `pager-next` as a
     *   link**, so "are there more pages" cannot be read from the pager alone here — see
     *   [io.github.nodyssey.core.html.SearchParser];
     * - the route is throttled to one request per two seconds, answering 429 beyond that.
     */
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

    /**
     * The `/member?t=<name>` shape an `@mention` link takes. Unlike [userSearchPath]'s `q=` (a
     * substring search, capped to a page of results), the site 302s `t=` straight to `/space/<uid>`
     * when the name matches exactly one account — the lookup [parseMemberName] links exist to trigger.
     */
    fun memberMentionPath(name: String): String = "/member?t=${name.urlEncode()}"

    const val SIGN_IN_PATH = "/signIn.html"

    /**
     * The Cloudflare Turnstile sitekey the sign-in form renders with.
     *
     * Read off the live page on 2026-08-24, not from documentation — `signIn.html` picks between two,
     * and the other one (`0x4AAAAAACYfzKxNfR026Cpk`) is only chosen when the page is served from
     * `localhost` or `127.0.0.1`. A sitekey is public by design; it is checked against the hostname
     * the widget is rendered on, which is why the app's own widget loads with [BASE_URL] as its base.
     */
    const val TURNSTILE_SITEKEY = "0x4AAAAAAAaNy7leGjewpVyR"

    /** Resolves site-relative URLs (`/avatar/1.png`) against the base URL; leaves absolute ones alone. */
    fun absoluteUrl(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> null

            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed

            // A `mailto:` names an address, not a place on the site, so the base URL has nothing to
            // add to it. Without this branch the fallback below made one anyway — an author's
            // `[联系我](mailto:a@b.com)` became `https://www.nodeseek.com/mailto:a@b.com`.
            trimmed.startsWith("mailto:", ignoreCase = true) -> trimmed

            trimmed.startsWith("//") -> "https:$trimmed"

            trimmed.startsWith("/") -> BASE_URL + trimmed

            else -> "$BASE_URL/$trimmed"
        }
    }

    /**
     * Our own site: the only URLs allowed to execute JavaScript inside the authenticated WebView, and
     * — the same question asked the other way round — the ones that open there rather than in a
     * browser.
     *
     * Both readings need the same answer, which is why one predicate serves both. A site page handed
     * to a Custom Tab is a page in the browser's cookie jar, where this account is not signed in and
     * where a Cloudflare pass lands out of the app's reach; see `openWebUrl` in `Navigation`.
     */
    fun isTrustedWebViewUrl(url: String): Boolean = isHttpsHost(url, TRUSTED_WEBVIEW_HOSTS)

    /**
     * Telegram's own authorisation host, the one 绑定 Telegram detours through.
     *
     * 联系方式's bind button is the site handing the user to `oauth.telegram.org`, which sends them
     * back to `/setting` with the widget's result. Both halves have to happen in the same web view:
     * the return leg is what writes `telegram_id` onto the account, and it only counts if it arrives
     * with the NodeSeek session — which lives in this app's cookie jar, not the browser's. Kept apart
     * from [isTrustedWebViewUrl] because sign-in and Cloudflare challenges have no business leaving
     * nodeseek.com; see the caller in `WebViewRoute`, which admits this host for 管理 pages only.
     */
    fun isTelegramOAuthUrl(url: String): Boolean = isHttpsHost(url, TELEGRAM_OAUTH_HOSTS)

    private fun isHttpsHost(url: String, hosts: Set<String>): Boolean =
        parseWebUrl(url)?.let { url ->
            url.scheme == "https" &&
                url.host?.lowercase() in hosts &&
                (url.port == -1 || url.port == 443)
        } == true

    /**
     * Is this a nodeseek.com link at all, regardless of scheme?
     *
     * Route recognition (post/space/mention) never touches the WebView or its cookies — a matched
     * route opens a native screen instead — so unlike [isTrustedWebViewUrl] it has no reason to
     * reject `http://`. Post bodies are user-submitted HTML kept verbatim for years; an old mention
     * or post link typed before the site enforced HTTPS survives as a literal `http://` anchor and
     * must still resolve internally instead of falling through to the browser.
     */
    private fun isOwnSiteUrl(url: String): Boolean =
        parseWebUrl(url)?.let { parsed ->
            (parsed.scheme == "https" || parsed.scheme == "http") &&
                parsed.host?.lowercase() in TRUSTED_WEBVIEW_HOSTS
        } == true

    /** Ordinary links leave the app and are accepted only when they are normal web URLs. */
    fun isExternalWebUrl(url: String): Boolean =
        parseWebUrl(url)?.let { parsed ->
            parsed.host != null && (parsed.scheme == "https" || parsed.scheme == "http")
        } == true

    /**
     * `mailto:` — the one non-web scheme a post can legitimately produce.
     *
     * Post content reaches a browser through a gate that admits `http`/`https` and nothing else,
     * which is what keeps an author's `javascript:` or `content://` from being handed to the
     * platform. An email address is the one thing that gate was refusing for no good reason: the
     * site's posts are full of them, and Cloudflare's obfuscation — undone in `SiteHtml` — turns the
     * ones written as links back into exactly this shape.
     *
     * Parsed rather than prefix-matched, so the same well-formedness rules every other link is held
     * to apply here too: no whitespace, no control characters, no `//` authority pretending to be an
     * address. A `mailto:` with nothing after the colon opens a blank draft and is refused.
     */
    fun isMailUrl(url: String): Boolean {
        val trimmed = url.trim()
        val parsed = parseWebUrl(trimmed) ?: return false
        if (parsed.scheme != "mailto" || parsed.host != null) return false
        return trimmed.substringAfter(':').substringBefore('?').isNotEmpty()
    }

    /**
     * The link as something with a host, or null when it is not a URL worth deciding about.
     *
     * A `userInfo` is refused rather than ignored: `https://www.nodeseek.com@evil.example/` names
     * `evil.example`, and every rule below is a rule about the host.
     */
    private fun parseWebUrl(url: String): WebUrl? = WebUrl.parse(url)?.takeIf { it.userInfo == null }

    private val POST_PATH = Regex("""/post-(\d+)(?:-(\d+))?""")
    private val SPACE_PATH = Regex("""/space/(\d+)""")
    private val TRUSTED_WEBVIEW_HOSTS = setOf("www.nodeseek.com", "nodeseek.com")
    private val TELEGRAM_OAUTH_HOSTS = setOf("oauth.telegram.org")

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

    /** A link into the site that the app can open on a screen of its own. */
    sealed interface InternalRoute {
        data class Post(val postId: Long, val page: Int) : InternalRoute
        data class Space(val uid: Long) : InternalRoute

        /** An `@mention` links `/member?t=<name>`, so only the name is known until it is resolved. */
        data class Member(val name: String) : InternalRoute

        /**
         * The 通知 tab. [group] is the list the link named, null when it named none the app knows.
         *
         * Null is not a failure: `/notification` on its own is the page as the site left it, and an
         * unrecognised fragment (`#/replyToMe` is the one the site has that this does not read) is
         * still a notification link, so the tab is a better answer than a web view of a screen the
         * app already has.
         */
        data class Notifications(val group: NotificationGroup?) : InternalRoute

        /** One 私信 conversation: `#/message?mode=talk&to=<uid>`. */
        data class MessageThread(val uid: Long) : InternalRoute
    }

    /**
     * Which of the 通知 lists a `/notification` link asks for.
     *
     * Deliberately not `data.NotificationCategory`: that enum carries the endpoints the repository
     * calls, which is a fact about fetching notifications rather than about reading a URL, and it
     * has a third value (回复我) no fragment here resolves to. The caller maps between them.
     */
    enum class NotificationGroup { MENTIONS, MESSAGES }

    /** Classifies a clicked link: an in-app destination when it is one of ours, null for the browser. */
    fun parseInternalRoute(url: String): InternalRoute? {
        if (!isOwnSiteUrl(url)) return null
        val unwrapped = unwrapJumpUrl(url)
        if (unwrapped != url) return parseInternalRoute(unwrapped)
        parsePostRoute(url)?.let { return InternalRoute.Post(it.postId, it.page) }
        parseUid(url)?.let { return InternalRoute.Space(it) }
        parseMemberName(url)?.let { return InternalRoute.Member(it) }
        return parseNotificationRoute(url)
    }

    /**
     * `/notification` and the hash routes the page keeps its own screens under.
     *
     * The site puts everything after `#`, which is the one part of a URL an `<intent-filter>` cannot
     * match on and the one part Android still delivers intact — so the manifest admits the path and
     * the reading of `#/atMe` versus `#/message?mode=talk&to=<uid>` happens here.
     *
     * Read off the fragment rather than pattern-matched whole because `mode=talk` is the site's way
     * of saying "a conversation, not the list" and `to` is the only part that names anything; a
     * `#/message` with no `to` is the conversation list, which is a group of the same screen.
     */
    private fun parseNotificationRoute(url: String): InternalRoute? {
        val parsed = parseWebUrl(url) ?: return null
        if (parsed.path?.trimEnd('/').orEmpty() != NOTIFICATION_PATH) return null
        val fragment = parsed.fragment.orEmpty()
        val name = fragment.substringBefore('?').trim('/')
        return when {
            name.equals("atMe", ignoreCase = true) ->
                InternalRoute.Notifications(NotificationGroup.MENTIONS)

            name.equals("message", ignoreCase = true) ->
                fragment.fragmentParameter("to")?.toLongOrNull()?.let(InternalRoute::MessageThread)
                    ?: InternalRoute.Notifications(NotificationGroup.MESSAGES)

            else -> InternalRoute.Notifications(null)
        }
    }

    /** The query half of a hash route — `#/message?mode=talk&to=5230` — which is not [WebUrl.rawQuery]. */
    private fun String.fragmentParameter(name: String): String? =
        substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.ifBlank { null }

    /**
     * The site wraps outbound links as `/jump?to=<encoded target>`. Returns the target for a jump
     * link on our own host, the URL unchanged otherwise — so a browser handoff opens the real
     * destination instead of the interstitial.
     */
    fun unwrapJumpUrl(url: String): String {
        val parsed = parseWebUrl(url) ?: return url
        if (parsed.host?.lowercase() !in TRUSTED_WEBVIEW_HOSTS || parsed.path != "/jump") return url
        val target = parsed.queryParameter("to") ?: return url
        return target.ifBlank { url }
    }

    private fun parseMemberName(url: String): String? =
        parseWebUrl(url)
            ?.takeIf { it.path == "/member" }
            ?.queryParameter("t")
            ?.ifBlank { null }
}

/**
 * A value going *into* one of the paths above, encoded the way `URLEncoder` encoded it.
 *
 * The unreserved set is `URLEncoder`'s and not `encodeURIComponent`'s — they differ over `~!'()`,
 * and these strings become search URLs the app has been building for releases. Written out rather
 * than delegated because `java.net.URLEncoder` is JVM API; the `+` it writes for a space was already
 * being corrected to `%20` at every call site, so that is simply done here.
 *
 * Top-level and `internal` rather than a private member of [NodeSeekSite], because [LuckyDraw] needs
 * the same answer: the draw link it builds is pasted into a public thread and opened by other people,
 * so it has to encode the way every URL this app has ever produced does.
 *
 * `StardustReceiveMarkup` has a near-twin of this with a different set, deliberately: that one has to
 * match the site's own editor character for character, and this one has to match the URLs already in
 * use.
 */
internal fun String.urlEncode(): String = buildString {
    for (byte in encodeToByteArray()) {
        val octet = byte.toInt() and 0xFF
        val char = octet.toChar()
        if (octet < 0x80 && (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in URL_ENCODER_SAFE)) {
            append(char)
        } else {
            append('%')
            append(HEX[octet shr 4])
            append(HEX[octet and 0xF])
        }
    }
}

private const val URL_ENCODER_SAFE = ".-*_"
private const val HEX = "0123456789ABCDEF"

/**
 * One level's chicken-leg span; see [NodeSeekSite.levelChickenSpan].
 *
 * [barRank] is the level the bar is drawn for, which is [NodeSeekSite.levelChickenSpan]'s clamped
 * rank rather than the account's — they differ only above Lv5, where the site stops advancing.
 */
data class LevelSpan(
    val barRank: Int,
    val floor: Int,
    val next: Int,
)
