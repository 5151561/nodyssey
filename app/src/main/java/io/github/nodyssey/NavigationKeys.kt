package io.github.nodyssey

import androidx.navigation3.runtime.NavKey
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

/** App notification polling (board f4) — a child of [SettingsKey]'s 通知 group. */
@Serializable
data object NotificationSettingsKey : NavKey

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
 * 图床 — the nodeimage.com connection.
 *
 * Under 账号设置 rather than the app's own [SettingsKey] because it is a credential for an account,
 * not a display preference; it sits beside 联系方式 for the same reason those do.
 */
@Serializable
data object AccountNodeImageKey : NavKey

@Serializable
data object PostComposerKey : NavKey

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
 */
@Serializable
data class PostDetailKey(
    val postId: Long,
    val floor: String? = null,
    val page: Int? = null,
) : NavKey

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
) : NavKey

/** 我的关注 / 我的粉丝. Only ever the signed-in user's — the site publishes nobody else's. */
@Serializable
data object FollowKey : NavKey

@Serializable
data class AssetsKey(
    val openAttendanceChooser: Boolean = false,
) : NavKey

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
