package io.github.nodyssey

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.assets.AssetsRoute
import io.github.nodyssey.ui.assets.AssetsViewModel
import io.github.nodyssey.ui.assets.CreditRoute
import io.github.nodyssey.ui.assets.CreditViewModel
import io.github.nodyssey.ui.assets.StardustRoute
import io.github.nodyssey.ui.assets.StardustViewModel
import io.github.nodyssey.ui.bookmarks.BookmarksRoute
import io.github.nodyssey.ui.bookmarks.BookmarksViewModel
import io.github.nodyssey.ui.history.ReadHistoryRoute
import io.github.nodyssey.ui.history.ReadHistoryViewModel
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.mycontent.MyCommentsRoute
import io.github.nodyssey.ui.mycontent.MyCommentsViewModel
import io.github.nodyssey.ui.mycontent.MyTopicsRoute
import io.github.nodyssey.ui.mycontent.MyTopicsViewModel
import io.github.nodyssey.ui.space.FollowRoute
import io.github.nodyssey.ui.space.FollowViewModel
import io.github.nodyssey.ui.space.UserSpaceRoute
import io.github.nodyssey.ui.space.UserSpaceViewModel

/**
 * A person and their things: the space page, and the 我的 lists — 收藏, 浏览历史, 关注, 资产.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.spaceEntries(nav: StackEntryScope) = with(nav) {
    entry<UserSpaceKey> { key ->
        val viewModel: UserSpaceViewModel =
            viewModel(
                // The landing tab is part of the identity: 我的主页 and 我的收藏 are the same
                // uid, and without it in the key the second would reuse the first's tab.
                key = "space-${key.uid}-${key.openCollections}",
                factory =
                UserSpaceViewModel.factory(container, key.uid, key.isSelf, key.openCollections),
            )
        UserSpaceRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onPostClick = { postId, floor -> backStack.add(PostDetailKey(postId, floor)) },
            // The same conversation screen 私信 opens from the notification tab (board 7f).
            // It reads and writes this thread through the site's own message endpoints, so
            // there is nothing here the web view was needed for.
            onMessage = { uid, name -> backStack.add(MessageThreadKey(uid, name)) },
            // 空间页右上角的笔是「编辑资料」，直接进资料编辑页，不是账号设置的目录页。
            onEditProfile = { backStack.add(AccountProfileFieldsKey) },
            onOpenBrowser = openWebUrl,
            onLinkClick = openContentUrl,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
        )
    }

    entry<BookmarksKey> {
        val viewModel: BookmarksViewModel =
            viewModel(factory = BookmarksViewModel.factory(container))
        BookmarksRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onPostClick = { postId -> backStack.add(PostDetailKey(postId)) },
            onOpenBrowser = openWebUrl,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE),
                )
            },
        )
    }

    entry<ReadHistoryKey> {
        val viewModel: ReadHistoryViewModel =
            viewModel(factory = ReadHistoryViewModel.factory(container))
        ReadHistoryRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onPostClick = { postId -> backStack.add(PostDetailKey(postId)) },
        )
    }

    entry<MyTopicsKey> {
        val viewModel: MyTopicsViewModel =
            viewModel(factory = MyTopicsViewModel.factory(container))
        MyTopicsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onPostClick = { postId -> backStack.add(PostDetailKey(postId)) },
            onBrowseFeed = openHomeTab,
            // `/space` needs the session's cookies, so a wall here is cleared in the app's own web
            // view rather than in the cookie-less system browser.
            onOpenBrowser = { backStack.add(WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE)) },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE)) },
        )
    }

    entry<MyCommentsKey> {
        val viewModel: MyCommentsViewModel =
            viewModel(factory = MyCommentsViewModel.factory(container))
        MyCommentsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // The floor rides along, so the thread opens on the comment rather than at its top.
            onCommentClick = { postId, floor -> backStack.add(PostDetailKey(postId, floor)) },
            onBrowseFeed = openHomeTab,
            onOpenBrowser = { backStack.add(WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE)) },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE)) },
        )
    }

    entry<FollowKey> { key ->
        val viewModel: FollowViewModel =
            viewModel(
                // The landing tab is part of the identity, as it is for a space page: 我的关注 and
                // 我的粉丝 are two tiles in 我的 and would otherwise share one view model whose tab
                // was set by whichever was opened first.
                key = "follow-${key.showFollowers}",
                factory = FollowViewModel.factory(container, key.showFollowers),
            )
        FollowRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onUserClick = openSpace,
            // `/fans` only exists for the signed-in user, so it follows the app's rule for
            // authenticated pages: the session's own web view, never the system browser.
            // The screen asks for this in one place — its error state — so the goal is the
            // one that closes itself once the wall is down.
            onOpenBrowser = { url ->
                backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
            },
            onSignIn = { backStack.add(SignInKey) },
        )
    }

    entry<AssetsKey> {
        val viewModel: AssetsViewModel =
            viewModel(factory = AssetsViewModel.factory(container))
        AssetsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onChickenLedger = { backStack.add(CreditKey) },
            onStardust = { backStack.add(StardustKey()) },
            // In-app, not the system browser: a Cloudflare pass earned out there lands in
            // Chrome's cookie store, and the app's own retry keeps failing forever.
            onOpenBrowser = {
                backStack.add(
                    WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE),
                )
            },
            onSignIn = { backStack.add(SignInKey) },
        )
    }

    entry<CreditKey> {
        val viewModel: CreditViewModel =
            viewModel(factory = CreditViewModel.factory(container))
        CreditRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // `/credit` needs the session's cookies, so it opens in the app's own web view
            // like every other authenticated page. It is now the fallback rather than the
            // destination: the native list above is the ledger, and this is where a
            // Cloudflare challenge gets solved so the native list can load.
            //
            // [WebViewGoal.CHALLENGE], not MANAGE, because solving it is the whole errand:
            // MANAGE waits for no cookie, so it sat there after the wall came down and left
            // the reader to work out they were done — with a way out to a real browser on
            // the toolbar, which is where a pass earned lands somewhere the app cannot read.
            onOpenBrowser = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.CREDIT_PATH,
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
            onSignIn = { backStack.add(SignInKey) },
        )
    }

    entry<StardustKey> { key ->
        val viewModel: StardustViewModel =
            viewModel(
                key = "stardust-${key.startTransfer}",
                factory = StardustViewModel.factory(container, key.startTransfer),
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // The ledger URL is per-member, so it exists only once the profile call has said who
        // we are; before that "在网页打开" can only offer the site's front page.
        val ledgerUrl = state.uid?.let { NodeSeekSite.BASE_URL + NodeSeekSite.stardustPath(it) }
        StardustRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // The ledger needs the session's cookies, so it opens in-app like every other
            // authenticated page rather than in the cookie-less system browser.
            onOpenBrowser = {
                backStack.add(
                    WebKey(ledgerUrl ?: NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.MANAGE),
                )
            },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        ledgerUrl ?: NodeSeekSite.BASE_URL,
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
        )
    }
}
