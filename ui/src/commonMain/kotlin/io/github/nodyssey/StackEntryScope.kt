package io.github.nodyssey

import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.assets.PendingAttendance
import io.github.nodyssey.ui.composer.PendingComposerShare
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.notifications.NotificationsViewModel
import io.github.nodyssey.ui.postlist.HomeFeedStates
import kotlinx.coroutines.CoroutineScope

/**
 * What every stack's [StackEntryScope] shares: the app-wide half, as opposed to the per-stack lambdas
 * [destinationProvider] binds on top of it.
 *
 * Its own type because two hosts build it. `MainNavigation` fills it from its composition; the iOS
 * shell fills it from plain objects, since there each screen is its own `UIViewController` and no
 * composition outlives them all. Everything that changes while the app runs crosses as a function,
 * for the reason [StackEntryScope] gives.
 */
internal class NavigationDependencies(
    val container: AppContainer,
    val siteTitle: String,
    val aboutSiteTitle: String,
    val privacyTitle: String,
    val rssLabel: String,
    val uriHandler: UriHandler,
    val openExternalUrl: (String) -> Unit,
    val notificationsViewModel: NotificationsViewModel,
    val homeFeedStates: HomeFeedStates,
    val homeReselectRequests: () -> Int,
    val notificationsScrollToTopRequests: () -> Int,
    val isListDetailExpanded: () -> Boolean,
    val isEinkMode: () -> Boolean,
    val onTabBarHiddenByScroll: (Boolean) -> Unit,
    /** Brings a tab to the front. The host owns which tab is current, so it owns the switch too. */
    val selectTab: (TopLevelDestination) -> Unit,
    /** Where a link that has to be resolved over the network waits for its answer. */
    val scope: CoroutineScope,
    /** What another app shared into a new post, waiting for the editor; one for the whole app. */
    val pendingComposerShare: PendingComposerShare,
    /** A launcher 签到 waiting for 账户与成长; one for the whole app. */
    val pendingAttendance: PendingAttendance,
)

/**
 * Everything an entry file needs from `MainNavigation`, bound to one tab's stack.
 *
 * The entries used to be one 700-line `entryProvider` block inside `MainNavigation`, where they
 * captured all of this for free out of the enclosing scope. Splitting them into files by region —
 * see `TabEntries.kt` and its siblings — means the capture has to be spelled out, and this class is
 * that spelling. It preserves the constraint the old closure enforced by construction: **one scope
 * per stack, built inside `destinationProvider`**, so every "open this somewhere" lambda is bound
 * to the same stack as the entry that calls it. A scope shared across stacks would resurrect the
 * bug the provider's own comment records — entries frozen over whichever tab happened to be
 * current when they were built.
 *
 * Values that change while the composition lives — the window's pane count, the scroll-to-top
 * counters — cross as functions rather than values, because an entry's content lambda is built in a
 * plain function: a `Boolean` field would freeze the answer at build time, while a function read
 * inside the composable body is a state read where it belongs.
 */
internal class StackEntryScope(
    val container: AppContainer,
    val backStack: NavBackStack<NavKey>,
    /** The app's display name, hoisted because entry lambdas cannot call `stringResource`. */
    val siteTitle: String,
    val aboutSiteTitle: String,
    val privacyTitle: String,
    val rssLabel: String,
    val signInUrl: String,
    val uriHandler: UriHandler,
    /** Shared with the tab badge and the 通知 root — one instance for the whole app. */
    val notificationsViewModel: NotificationsViewModel,
    /** 首页's per-board list states, owned by `MainNavigation` so Back reveals the same lists. */
    val homeFeedStates: HomeFeedStates,
    val homeReselectRequests: () -> Int,
    val notificationsScrollToTopRequests: () -> Int,
    val isListDetailExpanded: () -> Boolean,
    /**
     * 墨水屏模式, read through a lambda for the reason [isListDetailExpanded] is: the entry provider
     * beside this scope is built once and kept, so a value captured here would be whatever the
     * setting was at that moment, forever.
     */
    val isEinkMode: () -> Boolean,
    val onTabBarHiddenByScroll: (Boolean) -> Unit,
    /** A browser link — Custom Tab or the system browser, per the user's setting. */
    val openExternalUrl: (String) -> Unit,
    /** A web page routed by host: nodeseek.com stays in the app's web view. */
    val openWebUrl: (String) -> Unit,
    /** A user's space, with `isSelf` decided against the signed-in uid. */
    val openSpace: (Long) -> Unit,
    /** A content link: post/space/mention URLs get a native screen, the rest go to [openWebUrl]. */
    val openContentUrl: (String) -> Unit,
    /**
     * Switches to 首页, for the empty states whose one useful action is "go and read something".
     *
     * A tab switch rather than a push, for the reason the notification deep link gives: a tab is
     * not something a stack can hold, and pushing a second feed onto 我的's stack would leave Back
     * walking out of the feed into a profile.
     */
    val openHomeTab: () -> Unit,
    /** Switches to 我的, for the account avatar at the end of 首页's bar. A tab switch for the same reason as [openHomeTab]. */
    val openProfileTab: () -> Unit,
    /** What another app shared into a new post, waiting for the editor; one for the whole app. */
    val pendingComposerShare: PendingComposerShare,
    /** A launcher 签到 waiting for 账户与成长; one for the whole app. */
    val pendingAttendance: PendingAttendance,
)
