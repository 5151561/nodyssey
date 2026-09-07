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
import io.github.nodyssey.ui.profile.ProfileDestinations
import io.github.nodyssey.ui.profile.ProfileRoute
import io.github.nodyssey.ui.profile.ProfileViewModel
import io.github.nodyssey.ui.search.SearchRoute
import io.github.nodyssey.ui.search.SearchViewModel
import io.github.nodyssey.ui.settings.UpdateReminderViewModel

/**
 * The three tab roots, 搜索 — which 首页's app bar opens on top of itself — and the conversation
 * screen 通知 opens.
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
            feedStates = homeFeedStates,
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
            onSearch = { backStack.add(SearchKey) },
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
            onBack = { backStack.removeLastOrNull() },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
            // No `onNavigationBarHiddenChanged`: 搜索 is not a tab root any more, so on a phone the
            // bar is already gone while it is open and there is nothing for the scroll to hide.
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
            // Board n1 made 我的 a directory of twenty tiles, so where each one goes travels as one
            // object; see [ProfileDestinations] for why it is not twenty parameters.
            destinations =
            ProfileDestinations(
                settings = { backStack.add(SettingsKey) },
                space = {
                    container.profileRepository.selfUid.value?.let { uid ->
                        backStack.add(UserSpaceKey(uid, isSelf = true))
                    }
                },
                assets = { backStack.add(AssetsKey) },
                // 主题帖 and 评论 are screens of their own now (boards n2, n3) rather than tabs on
                // 个人主页: a tile that opens a page about *a user* and then picks a tab is exactly
                // the hop the grid exists to remove. The space page keeps both tabs — it is still
                // the only way to read anyone else's.
                topics = { backStack.add(MyTopicsKey) },
                comments = { backStack.add(MyCommentsKey) },
                // 我的收藏 has its own screen (board i1) rather than the space page's tab: it is
                // the only list here that is always about you, and the things the board asks for
                // — filters over the whole collection, multi-select, offline downloads — are
                // about the collection rather than about a profile.
                collections = { backStack.add(BookmarksKey) },
                history = { backStack.add(ReadHistoryKey) },
                following = { backStack.add(FollowKey()) },
                followers = { backStack.add(FollowKey(showFollowers = true)) },
                credit = { backStack.add(CreditKey) },
                stardust = { backStack.add(StardustKey()) },
                transfer = { backStack.add(StardustKey(startTransfer = true)) },
                invite = { backStack.add(InviteKey) },
                award = { backStack.add(AwardKey) },
                lucky = { backStack.add(LuckyKey) },
                ruling = { backStack.add(RulingKey) },
                // Both are pages the site renders itself and the app never parsed, so they open
                // where they always did: in a web view, not in a screen that would be a frame
                // around someone else's HTML.
                providers = { openWebUrl(NodeSeekSite.BASE_URL + NodeSeekSite.PROVIDERS_PATH) },
                friends = { openWebUrl(NodeSeekSite.BASE_URL + NodeSeekSite.FRIENDS_PATH) },
                blockList = { backStack.add(AccountBlockListKey) },
                aboutCommunity = { backStack.add(AboutCommunityKey) },
                accountSettings = { backStack.add(AccountSettingsKey) },
                notificationSettings = { backStack.add(NotificationSettingsKey) },
                themeSettings = { backStack.add(ThemeSettingsKey) },
                about = { backStack.add(AboutAppKey) },
                // Signed out only: the six links this page holds are tiles of their own above.
                tools = { backStack.add(CommunityToolsKey) },
            ),
            onSignIn = { backStack.add(SignInKey) },
            hasAppUpdate = hasUpdate,
            onOpenWebsite = { openWebUrl(NodeSeekSite.BASE_URL) },
            onVerify = {
                backStack.add(
                    WebKey(NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.CHALLENGE),
                )
            },
        )
    }
}
