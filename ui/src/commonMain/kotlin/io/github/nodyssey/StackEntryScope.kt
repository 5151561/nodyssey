package io.github.nodyssey

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.notifications.NotificationsViewModel

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
    /** 首页's list state, owned by `MainNavigation` so Back reveals the same list object. */
    val homeListState: LazyListState,
    val homeScrollToTopRequests: () -> Int,
    val notificationsScrollToTopRequests: () -> Int,
    val isListDetailExpanded: () -> Boolean,
    val onTabBarHiddenByScroll: (Boolean) -> Unit,
    /** A browser link — Custom Tab or the system browser, per the user's setting. */
    val openExternalUrl: (String) -> Unit,
    /** A web page routed by host: nodeseek.com stays in the app's web view. */
    val openWebUrl: (String) -> Unit,
    /** A user's space, with `isSelf` decided against the signed-in uid. */
    val openSpace: (Long) -> Unit,
    /** A content link: post/space/mention URLs get a native screen, the rest go to [openWebUrl]. */
    val openContentUrl: (String) -> Unit,
)
