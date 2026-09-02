package io.github.nodyssey

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.messages.MessageThreadRoute
import io.github.nodyssey.ui.messages.MessageThreadViewModel
import io.github.nodyssey.ui.notifications.NotificationsRoute
import io.github.nodyssey.ui.postlist.PostListRoute
import io.github.nodyssey.ui.postlist.PostListViewModel
import io.github.nodyssey.ui.profile.ProfileRoute
import io.github.nodyssey.ui.profile.ProfileViewModel
import io.github.nodyssey.ui.search.SearchRoute
import io.github.nodyssey.ui.search.SearchViewModel
import io.github.nodyssey.ui.settings.UpdateReminderViewModel

/**
 * The four tab roots and the conversation screen 通知 opens.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.tabRootEntries(nav: StackEntryScope) = with(nav) {
    entry<PostListKey> {
        val viewModel: PostListViewModel =
            viewModel(factory = PostListViewModel.factory(container))
        PostListRoute(
            viewModel = viewModel,
            listState = homeListState,
            // The whole of what the row is already showing, not just the id: the thread
            // draws these four before the network answers, and they are what the row's own
            // title, avatar, name and board tag fly into.
            onPostClick = { post ->
                backStack.add(
                    PostDetailKey(
                        post.summary.postId,
                        preview =
                        ThreadPreview(
                            title = post.summary.title,
                            authorName = post.summary.authorName,
                            avatarUrl = post.summary.avatarUrl,
                            categoryTitle = post.summary.categoryTitle,
                            categorySlug = post.summary.categorySlug,
                            isAwarded = post.summary.isAwarded,
                        ),
                    ),
                )
            },
            onCreatePost = { backStack.add(PostComposerKey()) },
            onSignIn = {
                backStack.add(SignInKey)
            },
            onVerify = {
                backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE))
            },
            onNavigationBarHiddenChanged = { hidden ->
                if (!isListDetailExpanded()) onTabBarHiddenByScroll(hidden)
            },
            reselectRequests = homeReselectRequests(),
        )
    }

    entry<SearchKey> {
        val viewModel: SearchViewModel =
            viewModel(factory = SearchViewModel.factory(container))
        SearchRoute(
            viewModel = viewModel,
            onPostClick = { backStack.add(PostDetailKey(it)) },
            // A user result now has a screen of its own, so it stays in the app. Finding
            // yourself in search must open the same self-shaped space 我的 opens.
            onUserClick = openSpace,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
            onNavigationBarHiddenChanged = { hidden ->
                if (!isListDetailExpanded()) onTabBarHiddenByScroll(hidden)
            },
        )
    }

    entry<NotificationsKey> {
        NotificationsRoute(
            viewModel = notificationsViewModel,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE),
                )
            },
            onNotificationClick = { notification ->
                notification.postId?.let {
                    backStack.add(PostDetailKey(it, notification.floor))
                }
            },
            onOpenThread = { uid, name ->
                backStack.add(MessageThreadKey(uid, name))
            },
            scrollToTopRequests = notificationsScrollToTopRequests(),
        )
    }

    entry<MessageThreadKey> { key ->
        val viewModel: MessageThreadViewModel =
            viewModel(
                key = "message-${key.uid}",
                factory =
                MessageThreadViewModel.factory(container, key.uid, key.userName),
            )
        MessageThreadRoute(
            viewModel = viewModel,
            showBackButton = !(isListDetailExpanded() && backStack.showsListPane()),
            onBack = { backStack.removeLastOrNull() },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH,
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
            onOpenBrowser = openWebUrl,
            // Pushed rather than swapped in: on a wide window the space lands in the list
            // pane and the conversation stays beside it, which is why `paneRoleOf` calls a
            // space a list wherever it is reached from.
            onOpenSpace = { openSpace(key.uid) },
            onLinkClick = openContentUrl,
        )
    }

    entry<ProfileKey> {
        val viewModel: ProfileViewModel =
            viewModel(factory = ProfileViewModel.factory(container))
        // Its own ViewModel rather than folded into ProfileViewModel: signing out rebuilds that
        // state from scratch, and whether a newer APK exists is not a fact about the session.
        val updateViewModel: UpdateReminderViewModel =
            viewModel(factory = UpdateReminderViewModel.factory(container))
        val hasUpdate by updateViewModel.hasUpdate.collectAsStateWithLifecycle()
        ProfileRoute(
            viewModel = viewModel,
            onSignIn = { backStack.add(SignInKey) },
            onSettings = { backStack.add(SettingsKey) },
            hasAppUpdate = hasUpdate,
            onAccountSettings = { backStack.add(AccountSettingsKey) },
            onOpenWebsite = { openWebUrl(NodeSeekSite.BASE_URL) },
            onVerify = {
                backStack.add(
                    WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE),
                )
            },
            onOpenSpace = { uid -> backStack.add(UserSpaceKey(uid, isSelf = true)) },
            // 我的收藏 has its own screen (board i1) rather than the space page's tab: it is
            // the only list here that is always about you, and the things the board asks for
            // — filters over the whole collection, multi-select, offline downloads — are
            // about the collection rather than about a profile. The space page keeps its tab.
            onCollections = { backStack.add(BookmarksKey) },
            onHistory = { backStack.add(ReadHistoryKey) },
            onAssets = { backStack.add(AssetsKey) },
            onFollow = { backStack.add(FollowKey) },
            onTools = { backStack.add(CommunityToolsKey) },
        )
    }
}
