package io.github.nodyssey

import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.data.composer.PostEditTarget
import io.github.nodyssey.ui.login.WebViewGoal
import kotlinx.serialization.Serializable

/** The four bottom-navigation destinations. */
@Serializable
data object PostListKey : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data object NotificationsKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object SettingsKey : NavKey

/**
 * 主题 — 明暗, 配色来源, the preset grid, 我的主题 and 色彩风格, with a live preview under them.
 *
 * A child of [SettingsKey]'s 外观 group. Its own screen rather than a block in that group because
 * every control on it changes the screen it is read on, and the preview card is only worth drawing
 * where there is room to watch it.
 */
@Serializable
data object ThemeSettingsKey : NavKey

/** 动态取色 — the wallpaper's candidates and the two switches over them. A child of [ThemeSettingsKey]. */
@Serializable
data object DynamicColorKey : NavKey

/** App notification polling (board f4) — a child of [SettingsKey]'s 通知 group. */
@Serializable
data object NotificationSettingsKey : NavKey

/**
 * 代理设置 — a child of [SettingsKey]'s 网络 group.
 *
 * Routes only the forum's own [io.github.nodyssey.di.AppContainer.okHttpClient], not the whole app;
 * see that property's doc for what does and does not go through it.
 */
@Serializable
data object ProxySettingsKey : NavKey

/**
 * 图床 — which image host is connected, and with what.
 *
 * A child of [SettingsKey] rather than of 账号设置: the host is not a NodeSeek account setting at all.
 * The forum stores Markdown and never sees the key, which lives on this device only, and it is
 * whichever host the person picked — so it belongs with the app's own settings, and stays reachable
 * while signed out.
 */
@Serializable
data object ImageHostKey : NavKey

@Serializable
data object AboutAppKey : NavKey

@Serializable
data object AboutCommunityKey : NavKey

@Serializable
data object PrivacyKey : NavKey

@Serializable
data object ChangelogKey : NavKey

@Serializable
data object OpenSourceLicensesKey : NavKey

/**
 * 账号设置 (8g) and its five sub-pages (d6 1–5/5).
 *
 * Separate from [SettingsKey], which is the app's own display preferences. The two are different
 * things that happen to share a word: one is what the site knows about the account, the other is how
 * this app draws it.
 */
@Serializable
data object AccountSettingsKey : NavKey

@Serializable
data object AccountProfileFieldsKey : NavKey

@Serializable
data object AccountSecurityKey : NavKey

/** 联系方式 (d6 3/5), including the Telegram binding flow (f3). */
@Serializable
data object AccountContactKey : NavKey

/** 屏蔽用户 (d6 4/5). */
@Serializable
data object AccountBlockListKey : NavKey

/** 偏好与首页版块 (d6 5/5). */
@Serializable
data object AccountPreferencesKey : NavKey

/**
 * The editor.
 *
 * [edit] null is 发布新帖; non-null rewrites a floor that is already up. Same screen either way —
 * the differences are small enough (no board, no draft, and no title on a reply) that a second one
 * would be the same 800 lines with three `if`s deleted.
 */
@Serializable
data class PostComposerKey(
    val edit: PostEditTarget? = null,
) : NavKey

/**
 * A private-message conversation (board 7f).
 *
 * [userName] travels with the key so the app bar has a title before the thread has loaded — the
 * conversation list already knew it, and re-deriving it from a network round trip would leave the
 * header blank for as long as that takes.
 */
@Serializable
data class MessageThreadKey(
    val uid: Long,
    val userName: String,
) : NavKey

/**
 * A thread, optionally opened somewhere other than its top.
 *
 * [floor] is what a notification or a quote knows — the site labels floors and leaves their page
 * implicit — and [page] is what a `/post-703863-4` link knows. Either one makes the detail screen
 * start there instead of at page 1; [floor] wins, being the more precise of the two.
 *
 * [preview] travels for the same reason [MessageThreadKey.userName] does: the list that opened this
 * already knew it, and a round trip to say it again leaves the screen blank meanwhile. Null exactly
 * where there was no row to carry it — a deep link, a notification, the composer returning a post
 * that did not exist a moment ago.
 */
@Serializable
data class PostDetailKey(
    val postId: Long,
    val floor: String? = null,
    val page: Int? = null,
    val preview: ThreadPreview? = null,
) : NavKey

/**
 * What a list row already knows about the thread it opens.
 *
 * Every field here is drawn twice — once in the row, once at the top of the thread — which is what
 * makes it worth carrying rather than re-fetching. Two things follow from having it:
 *
 * - The thread states these before the network answers, instead of four grey bars standing in for
 *   facts the app was already holding.
 * - They are what the row's own title, avatar, name and board tag fly into. A shared element needs
 *   something at the far end on the *first* frame of the flight, and on that frame the thread has
 *   nothing of its own; see [io.github.nodyssey.ui.common.LocalThreadTransition].
 *
 * Deliberately not the whole [io.github.nodyssey.model.PostSummary]: this goes in a navigation key,
 * which is serialized into saved state and survives process death, so it holds what the thread will
 * actually draw and nothing else. The reply count, the view count and 最后活跃 are facts about the
 * row, not about the thread, and the thread never shows them.
 */
@Serializable
data class ThreadPreview(
    val title: String,
    val authorName: String,
    val avatarUrl: String? = null,
    val categoryTitle: String? = null,
    val categorySlug: String? = null,
    /**
     * Carried although the thread draws it differently from the row — a labelled 推荐阅读 tag rather
     * than the row's diamond, so the two never travel into one another.
     *
     * It is here for a duller reason: the tag sits in the same wrapping row as the board tag, and a
     * loading state that did not know about it would size that row for two items and then find
     * three. Everything below — the avatar and the author's name, still settling out of their
     * flight — would step down as it re-wrapped.
     */
    val isAwarded: Boolean = false,
)

/**
 * A user's space.
 *
 * [isSelf] is carried rather than compared at render time because it changes the screen's shape, not
 * just its contents: the signed-in user gets a 收藏 tab and no 私信 button, and the site has no way to
 * read anyone else's collections. Deciding that from the session inside the screen would make the tab
 * row flicker on the first frame, before the profile call resolves.
 */
@Serializable
data class UserSpaceKey(
    val uid: Long,
    val isSelf: Boolean = false,
    /**
     * Opens on the 收藏 tab instead of the default one — what 我的收藏 in the profile menu means.
     *
     * A Boolean rather than a `SpaceTab`, so this file does not have to depend on `ui/space`. There
     * is exactly one tab anything links straight to, and inventing a serializable mirror of the enum
     * to express that would cost more than it explains.
     */
    val openCollections: Boolean = false,
) : NavKey

/** 我的关注 / 我的粉丝. Only ever the signed-in user's — the site publishes nobody else's. */
@Serializable
data object FollowKey : NavKey

/**
 * 我的收藏, as its own screen (board i1).
 *
 * No uid: the site publishes nobody else's collections, so this destination is only ever the signed-in
 * account's. That is also why it is separate from [UserSpaceKey] rather than a tab on it — the space
 * page is a page about *a user*, and this one cannot be about anyone but you.
 */
@Serializable
data object BookmarksKey : NavKey

/**
 * 浏览历史. Device-local, so unlike almost every other destination here it has no web equivalent to
 * fall back to — NodeSeek does not keep a reading history.
 */
@Serializable
data object ReadHistoryKey : NavKey

/**
 * 账户与成长.
 *
 * No arguments: it used to carry an `openAttendanceChooser` flag so 我的 could reach the sign-in
 * chooser by pushing this screen. 我的 signs in where it stands now, and a key whose only field
 * re-opened a dialog on every re-entry was the reason backing out of it looped.
 */
@Serializable
data object AssetsKey : NavKey

/** 鸡腿流水. Session-scoped like [StardustKey]: the site publishes no one else's ledger. */
@Serializable
data object CreditKey : NavKey

@Serializable
data object StardustKey : NavKey

@Serializable
data object CommunityToolsKey : NavKey

@Serializable
data object AwardKey : NavKey

@Serializable
data object LuckyKey : NavKey

@Serializable
data object InviteKey : NavKey

@Serializable
data object RulingKey : NavKey

/**
 * Full-screen image viewer.
 *
 * Carries the whole set of images in the thread plus which one was tapped, so swiping between them
 * needs no further trip to the data layer — the URLs were already parsed into the rendered content.
 */
@Serializable
data class ImageViewerKey(
    val urls: List<String>,
    val index: Int = 0,
) : NavKey

/**
 * Opens the restricted in-app browser for login and Cloudflare challenges.
 *
 * [goal] is what turns it from a browser into a step in a flow: it names the cookie the screen is
 * waiting for, so the screen can close itself once that cookie arrives instead of leaving the user to
 * work out that they are done.
 */
@Serializable
data class WebKey(
    val url: String,
    val title: String,
    val goal: WebViewGoal,
) : NavKey
