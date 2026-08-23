package io.github.nodyssey

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.composer.PostEditTarget
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.account.AccountSettingsRoute
import io.github.nodyssey.ui.account.AccountSettingsViewModel
import io.github.nodyssey.ui.account.BlockListRoute
import io.github.nodyssey.ui.account.BlockListViewModel
import io.github.nodyssey.ui.account.ContactRoute
import io.github.nodyssey.ui.account.ContactViewModel
import io.github.nodyssey.ui.account.ImageHostRoute
import io.github.nodyssey.ui.account.ImageHostViewModel
import io.github.nodyssey.ui.account.PreferencesRoute
import io.github.nodyssey.ui.account.PreferencesViewModel
import io.github.nodyssey.ui.account.ProfileFieldsRoute
import io.github.nodyssey.ui.account.ProfileFieldsViewModel
import io.github.nodyssey.ui.account.SecurityRoute
import io.github.nodyssey.ui.account.SecurityViewModel
import io.github.nodyssey.ui.assets.AssetsRoute
import io.github.nodyssey.ui.assets.AssetsViewModel
import io.github.nodyssey.ui.assets.CreditRoute
import io.github.nodyssey.ui.assets.CreditViewModel
import io.github.nodyssey.ui.assets.StardustRoute
import io.github.nodyssey.ui.assets.StardustViewModel
import io.github.nodyssey.ui.bookmarks.BookmarksRoute
import io.github.nodyssey.ui.bookmarks.BookmarksViewModel
import io.github.nodyssey.ui.common.LocalThreadTransition
import io.github.nodyssey.ui.common.appName
import io.github.nodyssey.ui.composer.PostComposerRoute
import io.github.nodyssey.ui.composer.PostComposerViewModel
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.history.ReadHistoryRoute
import io.github.nodyssey.ui.history.ReadHistoryViewModel
import io.github.nodyssey.ui.login.SignInRoute
import io.github.nodyssey.ui.login.SignInViewModel
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.login.WebViewRoute
import io.github.nodyssey.ui.messages.MessageThreadRoute
import io.github.nodyssey.ui.messages.MessageThreadViewModel
import io.github.nodyssey.ui.navigation.NodysseyNavigationItems
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.notifications.NotificationsRoute
import io.github.nodyssey.ui.notifications.NotificationsViewModel
import io.github.nodyssey.ui.postdetail.PostDetailRoute
import io.github.nodyssey.ui.postdetail.PostDetailViewModel
import io.github.nodyssey.ui.postlist.PostListRoute
import io.github.nodyssey.ui.postlist.PostListViewModel
import io.github.nodyssey.ui.profile.ProfileRoute
import io.github.nodyssey.ui.profile.ProfileViewModel
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.about_privacy
import io.github.nodyssey.ui.resources.about_rss
import io.github.nodyssey.ui.resources.about_site
import io.github.nodyssey.ui.resources.home_pane_empty
import io.github.nodyssey.ui.resources.notifications_pane_empty
import io.github.nodyssey.ui.resources.search_pane_empty
import io.github.nodyssey.ui.resources.space_pane_empty
import io.github.nodyssey.ui.search.SearchRoute
import io.github.nodyssey.ui.search.SearchViewModel
import io.github.nodyssey.ui.settings.AboutAppRoute
import io.github.nodyssey.ui.settings.AboutAppViewModel
import io.github.nodyssey.ui.settings.AboutCommunityScreen
import io.github.nodyssey.ui.settings.AppLinks
import io.github.nodyssey.ui.settings.ChangelogRoute
import io.github.nodyssey.ui.settings.ChangelogViewModel
import io.github.nodyssey.ui.settings.CommunityLinks
import io.github.nodyssey.ui.settings.DohSettingsRoute
import io.github.nodyssey.ui.settings.DohSettingsViewModel
import io.github.nodyssey.ui.settings.NotificationSettingsRoute
import io.github.nodyssey.ui.settings.NotificationSettingsViewModel
import io.github.nodyssey.ui.settings.OpenSourceLicensesScreen
import io.github.nodyssey.ui.settings.PrivacyRoute
import io.github.nodyssey.ui.settings.PrivacyViewModel
import io.github.nodyssey.ui.settings.ProxySettingsRoute
import io.github.nodyssey.ui.settings.ProxySettingsViewModel
import io.github.nodyssey.ui.settings.SettingsRoute
import io.github.nodyssey.ui.settings.SettingsViewModel
import io.github.nodyssey.ui.settings.UpdateReminderDialog
import io.github.nodyssey.ui.settings.theme.DynamicColorRoute
import io.github.nodyssey.ui.settings.theme.ThemeSettingsRoute
import io.github.nodyssey.ui.settings.theme.ThemeSettingsViewModel
import io.github.nodyssey.ui.space.FollowRoute
import io.github.nodyssey.ui.space.FollowViewModel
import io.github.nodyssey.ui.space.UserSpaceRoute
import io.github.nodyssey.ui.space.UserSpaceViewModel
import io.github.nodyssey.ui.stardust.StardustReceiveCard
import io.github.nodyssey.ui.stardust.StardustReceiveViewModel
import io.github.nodyssey.ui.tools.AwardRoute
import io.github.nodyssey.ui.tools.AwardViewModel
import io.github.nodyssey.ui.tools.CommunityToolsScreen
import io.github.nodyssey.ui.tools.InviteRoute
import io.github.nodyssey.ui.tools.InviteViewModel
import io.github.nodyssey.ui.tools.LuckyRoute
import io.github.nodyssey.ui.tools.LuckyViewModel
import io.github.nodyssey.ui.tools.RulingRoute
import io.github.nodyssey.ui.tools.RulingViewModel
import io.github.nodyssey.ui.viewer.ImageViewerScreen
import io.github.nodyssey.ui.viewer.ImageViewerViewModel
import io.github.nodyssey.ui.viewer.rememberImageGallerySaver
import io.github.nodyssey.ui.vote.VoteCard
import io.github.nodyssey.ui.vote.VoteViewModel
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.component.rememberSilentClipboardCopy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainNavigation(
    container: AppContainer,
    modifier: Modifier = Modifier,
    initialTab: TopLevelDestination = TopLevelDestination.HOME,
    launchRequest: LaunchRequest? = null,
    onLaunchRequestHandled: () -> Unit = {},
) {
    val signInUrl = NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH

    // Hoisted out of the navigation lambdas below, which are not composable.
    val siteTitle = appName()
    val aboutSiteTitle = stringResource(Res.string.about_site)
    val privacyTitle = stringResource(Res.string.about_privacy)
    val rssLabel = stringResource(Res.string.about_rss)
    val uriHandler = LocalUriHandler.current
    /*
     * Hands a link to the browser — a Custom Tab or the system one, per the user's setting.
     *
     * Narrow on purpose. Anything on nodeseek.com should go through `openWebUrl` below instead; what
     * is left here is the handful of links that genuinely want a browser: an image the user means to
     * download or share, the terms of service, and the escape hatch out of the web view itself.
     *
     * `mailto:` is admitted alongside the web schemes because a post may carry an address the author
     * wrote as a link. The Custom Tab declines it — see `usesCustomTab` — and the platform handler
     * passes it to whatever writes mail on this device; the gate stays shut on every other scheme.
     */
    val openExternalUrl: (String) -> Unit = remember(uriHandler) {
        { url ->
            if (NodeSeekSite.isExternalWebUrl(url) || NodeSeekSite.isMailUrl(url)) {
                runCatching { uriHandler.openUri(url) }
            }
        }
    }
    val notificationsViewModel: NotificationsViewModel =
        viewModel(factory = NotificationsViewModel.factory(container))
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    // The tab badge is only as fresh as the last count fetch, and nothing used to fetch again once
    // the app was running. This root outlives every tab, so its ON_RESUME is "the app came back to
    // the foreground" — the moment a badge grown stale overnight is most visibly wrong.
    LifecycleResumeEffect(Unit) {
        notificationsViewModel.refreshIfStale()
        onPauseOrDispose {}
    }

    /*
     * One back stack per tab, rather than one shared stack that gets cleared on every switch.
     *
     * Clearing was cheaper but it threw away the entry, and with it everything the
     * SaveableStateHolder was keeping for that entry — the list's scroll offset above all. Leaving
     * a thread half-read to glance at another tab and coming back to the top of the feed is the
     * regression that costs the most and shows up the fastest.
     *
     * Written out rather than built in a loop: four `remember` calls whose order can never drift
     * are easier to be sure about than a map comprehension that happens to call `remember` inside
     * an inline lambda.
     */
    val homeStack = rememberNavBackStack(NavKeySavedStateConfiguration, PostListKey)
    val searchStack = rememberNavBackStack(NavKeySavedStateConfiguration, SearchKey)
    val notificationsStack = rememberNavBackStack(NavKeySavedStateConfiguration, NotificationsKey)
    val profileStack = rememberNavBackStack(NavKeySavedStateConfiguration, ProfileKey)

    /*
     * The feed's position belongs to the home stack, not to whichever home entry composition happens
     * to be visible. A compact NavDisplay removes the list while a thread is on screen, so the state
     * lives out here and Back reveals the same list object rather than a rebuilt one.
     *
     * Worth being honest about what this does and does not buy: it was added to fix "returning from a
     * thread lands near the top of the feed" and it did not, because that was never about where the
     * state lived — the pager was renumbering the rows underneath it. See
     * [io.github.nodyssey.data.OfflineFirstPostRepository.FEED_PAGING_CONFIG] for the actual cause.
     * This stays because holding the state here is still the clearer ownership, and it survives a tab
     * switch without depending on SaveableStateHolder timing.
     */
    val homeListState = rememberLazyListState()

    var currentTab by rememberSaveable { mutableStateOf(initialTab) }

    /*
     * Set by whichever tab root is currently reading a long list — the feed and search both do.
     *
     * One flag rather than one per tab because only one of them is on screen at a time, and each
     * clears it on the way out: leaving the composition is what a tab switch looks like from inside a
     * screen, so the tab being switched *to* never inherits a bar the previous one had hidden.
     *
     * Transient by design: rotation should not restore a navigation bar hidden by an old gesture.
     */
    var tabBarHiddenByScroll by remember { mutableStateOf(false) }

    /*
     * Tapping 首页 while already on 首页 is the platform's "back to the top" gesture, and until now it
     * was the one place in the bar where a tap did nothing at all. 通知 answers the same tap the same
     * way; the two counters are separate because a tap on one tab is not a request to the other.
     *
     * A counter rather than a boolean flag: two taps in a row are two separate requests, and a flag
     * would need clearing afterwards — which is a second write the screen would have to own. Saved
     * rather than merely remembered, so that it survives a rotation alongside the screen's record of
     * which request it has already answered; a counter that reset while that record did not would
     * look like a fresh tap and scroll a restored list back to the top.
     */
    var homeScrollToTopRequests by rememberSaveable { mutableIntStateOf(0) }
    var notificationsScrollToTopRequests by rememberSaveable { mutableIntStateOf(0) }

    val backStack: NavBackStack<NavKey> =
        when (currentTab) {
            TopLevelDestination.HOME -> homeStack
            TopLevelDestination.SEARCH -> searchStack
            TopLevelDestination.NOTIFICATIONS -> notificationsStack
            TopLevelDestination.PROFILE -> profileStack
        }

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val paneDirective = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val isListDetailExpanded = paneDirective.maxHorizontalPartitions > 1
    val currentListDetailExpanded by rememberUpdatedState(isListDetailExpanded)
    val listDetailSceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(directive = paneDirective)

    // The bar belongs to the top-level destinations only. A thread, the image viewer and the web
    // view are full-screen by design — showing a tab bar under them would invite leaving mid-read.
    //
    // Two panes changes what "under them" means: a detail is drawn *beside* its list, not over it,
    // so the tab root is still on screen and the bar still belongs to it. The question is therefore
    // whether the top of the stack is part of a pane scene at all, not which tab it belongs to —
    // 首页, 搜索, 通知 and a user's space all pair with a detail now.
    val atTabRoot = TopLevelDestination.forKey(backStack.lastOrNull()) != null
    val showNavigationSuite =
        (atTabRoot && (!tabBarHiddenByScroll || isListDetailExpanded)) ||
            (isListDetailExpanded && paneRoleOf(backStack.lastOrNull()) != null)
    val navigationSuiteState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(showNavigationSuite) {
        if (showNavigationSuite) navigationSuiteState.show() else navigationSuiteState.hide()
    }

    val scope = rememberCoroutineScope()

    /*
     * Where something outside the app has asked us to go.
     *
     * Onto 首页 rather than whichever tab happened to be open: a link arriving from elsewhere has no
     * relationship to the tab the user last left behind, and landing a thread on top of 账号设置 would
     * make Back walk out through a settings page. 首页's stack starts as `[PostListKey]`, so a cold
     * start lands exactly the two layers the deep link should have — the list, then the thread.
     *
     * Nothing is cleared first. When the app was already running the user has a place in 首页 worth
     * keeping, and Back still reaches the list eventually; throwing it away to make the stack exactly
     * two entries deep would cost more than the tidiness is worth.
     *
     * `onLaunchRequestHandled` is what stops a rotation from replaying the link: the Activity holds
     * the request until the composition says it is spent.
     */
    LaunchedEffect(launchRequest) {
        val openSpaceOnHome: (Long) -> Unit = { uid ->
            homeStack.add(UserSpaceKey(uid, isSelf = uid == container.profileRepository.selfUid))
        }
        when (val request = launchRequest) {
            null -> return@LaunchedEffect

            is LaunchRequest.OpenTab -> currentTab = request.tab

            is LaunchRequest.OpenLink -> {
                val route = NodeSeekSite.parseInternalRoute(request.url)
                /*
                 * A notification link is the one kind that has a tab of its own to land on, and 首页
                 * would be the wrong one twice over: the screen it asks for is 通知's, and Back from
                 * a conversation would walk out through the feed.
                 */
                currentTab =
                    when (route) {
                        is NodeSeekSite.InternalRoute.Notifications,
                        is NodeSeekSite.InternalRoute.MessageThread,
                        -> TopLevelDestination.NOTIFICATIONS

                        else -> TopLevelDestination.HOME
                    }
                // The web view is the fallback rather than the browser: the link came to us because
                // the user chose this app for it, and bouncing it back out would read as a refusal.
                val openInWebView: () -> Unit = {
                    homeStack.add(WebKey(request.url, siteTitle, WebViewGoal.MANAGE))
                }
                when (route) {
                    is NodeSeekSite.InternalRoute.Post ->
                        homeStack.add(PostDetailKey(route.postId, page = route.page))

                    is NodeSeekSite.InternalRoute.Space -> openSpaceOnHome(route.uid)

                    is NodeSeekSite.InternalRoute.Member ->
                        resolveMemberLink(
                            name = route.name,
                            resolveMemberUid = container.searchRepository::resolveMemberUid,
                            onResolved = openSpaceOnHome,
                            onFailure = openInWebView,
                        )

                    is NodeSeekSite.InternalRoute.Notifications ->
                        route.group?.let { notificationsViewModel.selectCategory(it.toCategory()) }

                    is NodeSeekSite.InternalRoute.MessageThread -> {
                        // The list behind the conversation, so Back lands on 私信 rather than on
                        // whichever group the tab was last left showing.
                        notificationsViewModel.selectCategory(NotificationCategory.MESSAGES)
                        notificationsStack.openMessageThread(route.uid)
                    }

                    // Only reachable if the manifest filter and `parseInternalRoute` drift apart.
                    null -> openInWebView()
                }
            }
        }
        onLaunchRequestHandled()
    }

    fun destinationEntries(
        backStack: NavBackStack<NavKey>,
        openWebUrl: (String) -> Unit,
        openSpace: (Long) -> Unit,
        openContentUrl: (String) -> Unit,
    ) =
        entryProvider<NavKey> {
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
                        if (!currentListDetailExpanded) tabBarHiddenByScroll = hidden
                    },
                    scrollToTopRequests = homeScrollToTopRequests,
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
                    onUserClick = { uid ->
                        backStack.add(
                            UserSpaceKey(uid, isSelf = uid == container.profileRepository.selfUid),
                        )
                    },
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                    onNavigationBarHiddenChanged = { hidden ->
                        if (!currentListDetailExpanded) tabBarHiddenByScroll = hidden
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
                    scrollToTopRequests = notificationsScrollToTopRequests,
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
                    showBackButton = !(currentListDetailExpanded && backStack.showsListPane()),
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
                // Read here rather than folded into ProfileViewModel: signing out rebuilds that
                // state from scratch, and whether a newer APK exists is not a fact about the session.
                val updateState by container.appUpdateRepository.state.collectAsStateWithLifecycle()
                ProfileRoute(
                    viewModel = viewModel,
                    onSignIn = { backStack.add(SignInKey) },
                    onSettings = { backStack.add(SettingsKey) },
                    hasAppUpdate = updateState.available != null,
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

            entry<SettingsKey> {
                val viewModel: SettingsViewModel =
                    viewModel(factory = SettingsViewModel.factory(container))
                SettingsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenTheme = { backStack.add(ThemeSettingsKey) },
                    onOpenNotifications = { backStack.add(NotificationSettingsKey) },
                    onOpenProxy = { backStack.add(ProxySettingsKey) },
                    onOpenDoh = { backStack.add(DohSettingsKey) },
                    onOpenImageHost = { backStack.add(ImageHostKey) },
                    onOpenAbout = { backStack.add(AboutAppKey) },
                    onOpenLicenses = { backStack.add(OpenSourceLicensesKey) },
                )
            }

            entry<ThemeSettingsKey> {
                val viewModel: ThemeSettingsViewModel =
                    viewModel(factory = ThemeSettingsViewModel.factory(container))
                ThemeSettingsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDynamicColor = { backStack.add(DynamicColorKey) },
                )
            }

            entry<DynamicColorKey> {
                val viewModel: ThemeSettingsViewModel =
                    viewModel(factory = ThemeSettingsViewModel.factory(container))
                DynamicColorRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<NotificationSettingsKey> {
                val viewModel: NotificationSettingsViewModel =
                    viewModel(factory = NotificationSettingsViewModel.factory(container))
                NotificationSettingsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    // 绑定 Telegram lives on 联系方式 (d6 3/4), the site's own binding entry.
                    onOpenTelegram = { backStack.add(AccountContactKey) },
                )
            }

            entry<ProxySettingsKey> {
                val viewModel: ProxySettingsViewModel =
                    viewModel(factory = ProxySettingsViewModel.factory(container))
                ProxySettingsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<DohSettingsKey> {
                // Null on a platform that cannot apply a DoH server at all, where the row in 设置
                // that leads here is not drawn either. Nothing is drawn if it is reached some other
                // way, which is the honest answer for a screen whose every control would write a
                // setting nothing reads.
                container.doh?.let { doh ->
                    val viewModel: DohSettingsViewModel =
                        viewModel(factory = DohSettingsViewModel.factory(doh))
                    DohSettingsRoute(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }

            entry<AboutAppKey> {
                val viewModel: AboutAppViewModel =
                    viewModel(factory = AboutAppViewModel.factory(container))
                AboutAppRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenChangelog = { backStack.add(ChangelogKey) },
                    onOpenLicenses = { backStack.add(OpenSourceLicensesKey) },
                    onOpenUri = openExternalUrl,
                )
            }

            entry<AboutCommunityKey> {
                val copyRss = rememberSilentClipboardCopy()
                AboutCommunityScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenAboutSite = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.ABOUT_PATH,
                                aboutSiteTitle,
                                WebViewGoal.MANAGE,
                            ),
                        )
                    },
                    onOpenPrivacy = { backStack.add(PrivacyKey) },
                    onOpenUri = { uri ->
                        if (NodeSeekSite.isExternalWebUrl(uri)) {
                            openExternalUrl(uri)
                        } else if (uri == CommunityLinks.EMAIL) {
                            runCatching { uriHandler.openUri(uri) }
                        }
                    },
                    // The screen shows its own snackbar, so this is the copy that stays quiet.
                    onCopyRss = { copyRss(rssLabel, CommunityLinks.RSS) },
                )
            }

            entry<PrivacyKey> {
                val privacyViewModel: PrivacyViewModel =
                    viewModel(factory = PrivacyViewModel.factory(container))
                PrivacyRoute(
                    viewModel = privacyViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    // 「在浏览器中打开原文」 says the browser and means it — the terms are a public
                    // page, and a reader checking what they agreed to may well want it outside the
                    // app that is quoting it. The web view below is the fallback for when no
                    // browser takes the intent.
                    onOpenOriginal = {
                        openExternalUrl(NodeSeekSite.BASE_URL + NodeSeekSite.TERMS_OF_SERVICE_PATH)
                    },
                    onOpenWebFallback = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.TERMS_OF_SERVICE_PATH,
                                privacyTitle,
                                WebViewGoal.MANAGE,
                            ),
                        )
                    },
                )
            }

            entry<ChangelogKey> {
                val changelogViewModel: ChangelogViewModel =
                    viewModel(factory = ChangelogViewModel.factory(container))
                ChangelogRoute(
                    viewModel = changelogViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenReleases = { openExternalUrl(AppLinks.RELEASES) },
                    onOpenUri = { openExternalUrl(it) },
                )
            }

            entry<OpenSourceLicensesKey> {
                OpenSourceLicensesScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenUri = openExternalUrl,
                )
            }

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

            entry<FollowKey> {
                val viewModel: FollowViewModel =
                    viewModel(factory = FollowViewModel.factory(container))
                FollowRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onUserClick = { uid ->
                        backStack.add(
                            UserSpaceKey(uid, isSelf = uid == container.profileRepository.selfUid),
                        )
                    },
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
                    onStardust = { backStack.add(StardustKey) },
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

            entry<StardustKey> {
                val viewModel: StardustViewModel =
                    viewModel(factory = StardustViewModel.factory(container))
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

            entry<AccountSettingsKey> {
                val viewModel: AccountSettingsViewModel =
                    viewModel(factory = AccountSettingsViewModel.factory(container))
                AccountSettingsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenProfileFields = { backStack.add(AccountProfileFieldsKey) },
                    onOpenSecurity = { backStack.add(AccountSecurityKey) },
                    onOpenContact = { backStack.add(AccountContactKey) },
                    onOpenBlockList = { backStack.add(AccountBlockListKey) },
                    // 常用偏好 and 首页版块 share one page (d6 5/5): three of their rows are the
                    // same account-side switches, and splitting them would leave two stub screens.
                    onOpenPreferences = { backStack.add(AccountPreferencesKey) },
                )
            }

            entry<ImageHostKey> {
                val viewModel: ImageHostViewModel =
                    viewModel(factory = ImageHostViewModel.factory(container))
                ImageHostRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    // Every image host is a different site with a different session; the in-app web
                    // view exists to carry NodeSeek's cookies and has no business holding these.
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                )
            }

            entry<AccountProfileFieldsKey> {
                val viewModel: ProfileFieldsViewModel =
                    viewModel(factory = ProfileFieldsViewModel.factory(container))
                ProfileFieldsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_INTRODUCTION),
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                )
            }

            entry<AccountSecurityKey> {
                val viewModel: SecurityViewModel =
                    viewModel(factory = SecurityViewModel.factory(container))
                SecurityRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    // `otpauth://` belongs to whichever authenticator app registered for it, so it
                    // leaves the app rather than being rendered here — the app never holds the
                    // secret. Returns false when nothing claims the scheme, so the screen can say so
                    // instead of appearing to do nothing.
                    onOpenEnrolmentUri = { uri ->
                        runCatching { uriHandler.openUri(uri) }.isSuccess
                    },
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_SECURITY),
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                )
            }

            entry<AccountContactKey> {
                val viewModel: ContactViewModel =
                    viewModel(factory = ContactViewModel.factory(container))
                ContactRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    // 修改邮箱 and 绑定 Telegram are errands on nodeseek.com's own settings page, so
                    // they go to the web view that shares the app's session. A Custom Tab would hand
                    // them to the browser's cookie jar, where the account is not signed in.
                    onOpenSite = { url, forBinding ->
                        backStack.add(
                            WebKey(
                                url,
                                siteTitle,
                                if (forBinding) WebViewGoal.TELEGRAM_BIND else WebViewGoal.MANAGE,
                            ),
                        )
                    },
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_CONTACT),
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                )
            }

            entry<AccountBlockListKey> {
                val viewModel: BlockListViewModel =
                    viewModel(factory = BlockListViewModel.factory(container))
                BlockListRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_BLOCK),
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                )
            }

            entry<AccountPreferencesKey> {
                val viewModel: PreferencesViewModel =
                    viewModel(factory = PreferencesViewModel.factory(container))
                PreferencesRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_PREFERENCE),
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                )
            }

            entry<CommunityToolsKey> {
                CommunityToolsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onAward = { backStack.add(AwardKey) },
                    onProviders = { openWebUrl(NodeSeekSite.BASE_URL + NodeSeekSite.PROVIDERS_PATH) },
                    onFriends = { openWebUrl(NodeSeekSite.BASE_URL + NodeSeekSite.FRIENDS_PATH) },
                    onLucky = { backStack.add(LuckyKey) },
                    onInvite = { backStack.add(InviteKey) },
                    onRuling = { backStack.add(RulingKey) },
                    onAboutCommunity = { backStack.add(AboutCommunityKey) },
                )
            }

            entry<AwardKey> {
                val viewModel: AwardViewModel =
                    viewModel(factory = AwardViewModel.factory(container))
                AwardRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onPostClick = { backStack.add(PostDetailKey(it)) },
                    // 加精 asks for a web view in one place only — the error state — so this is a
                    // challenge to be cleared rather than a page to be browsed, and `openWebUrl`'s
                    // MANAGE would have left it open with nothing saying the errand was finished.
                    onOpenBrowser = { url ->
                        backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
                    },
                    onSignIn = { backStack.add(SignInKey) },
                )
            }

            entry<LuckyKey> {
                val viewModel: LuckyViewModel =
                    viewModel(factory = LuckyViewModel.factory(container))
                LuckyRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBrowser = openWebUrl,
                )
            }

            entry<InviteKey> {
                val viewModel: InviteViewModel =
                    viewModel(factory = InviteViewModel.factory(container))
                InviteRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onBuyOnSite = { backStack.add(inviteWebKey(siteTitle)) },
                )
            }

            entry<RulingKey> {
                val viewModel: RulingViewModel =
                    viewModel(factory = RulingViewModel.factory(container))
                RulingRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onPostClick = { postId, floor ->
                        backStack.add(PostDetailKey(postId, floor = floor?.toString()))
                    },
                    onUserClick = openSpace,
                    onOpenBrowser = openWebUrl,
                    onVerify = { url ->
                        backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
                    },
                    onSignIn = { backStack.add(SignInKey) },
                )
            }

            entry<ImageViewerKey>(
                /*
                 * Fades rather than the default slide.
                 *
                 * Every other destination is a page that follows the one before it, and sliding says
                 * so. This one is the picture already on screen, filled out — it is the same object
                 * seen closer, not a place the user travelled to, and sliding it in from the edge
                 * reads as having left the thread rather than having zoomed into it.
                 */
                metadata =
                NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
                    NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
                    NavDisplay.predictivePopTransitionSpec { _ ->
                        fadeIn() togetherWith fadeOut()
                    },
            ) { key ->
                val saver = rememberImageGallerySaver(container.dispatchers)
                val viewModel: ImageViewerViewModel =
                    viewModel(factory = ImageViewerViewModel.factory(saver))
                val saveOutcome by viewModel.saveOutcome.collectAsStateWithLifecycle()
                ImageViewerScreen(
                    urls = key.urls,
                    initialIndex = key.index,
                    onClose = { backStack.removeLastOrNull() },
                    // The browser, even for an image hosted on nodeseek.com: this one is the user
                    // reaching for downloading and sharing, which is the browser's job and not
                    // something the session's web view does better.
                    onOpenBrowser = openExternalUrl,
                    saveOutcome = saveOutcome,
                    onSave = viewModel::save,
                )
            }

            entry<PostDetailKey>(
                /*
                 * Fades rather than the default slide — but only for a thread opened from a row that
                 * handed over its title.
                 *
                 * A slide and a shared element contradict each other: the title would detach from
                 * the page it belongs to and fly across a screen that is itself travelling sideways.
                 * Fading the two pages leaves the title as the only thing moving, which is the whole
                 * point of moving it. Where no row supplied a title there is nothing to fly — a
                 * notification, a deep link, the composer — and those keep the slide, which is still
                 * the honest description of what happened: a page arrived from somewhere else.
                 */
                metadata = { key ->
                    if (key.preview == null) emptyMap() else ThreadOpenTransition
                },
            ) { key ->
                // Keyed so navigating to a different post builds a fresh ViewModel.
                val viewModel: PostDetailViewModel =
                    viewModel(
                        key = "post-${key.postId}",
                        factory =
                        PostDetailViewModel.factory(
                            container,
                            key.postId,
                            initialFloor = key.floor,
                            initialPage = key.page,
                            preview = key.preview,
                        ),
                    )
                // Its own ViewModel, keyed the same way: an unsent reply belongs to one thread
                // and has to outlive the sheet that shows it.
                val replyViewModel: ReplyComposerViewModel =
                    viewModel(
                        key = "reply-${key.postId}",
                        factory = ReplyComposerViewModel.factory(container, key.postId),
                    )
                PostDetailRoute(
                    viewModel = viewModel,
                    replyViewModel = replyViewModel,
                    showBackButton = !(currentListDetailExpanded && backStack.showsListPane()),
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBrowser = openWebUrl,
                    onLinkClick = openContentUrl,
                    onAuthorClick = openSpace,
                    onSignIn = { backStack.add(SignInKey) },
                    onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                    onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
                    onEdit = { target -> backStack.add(PostComposerKey(target)) },
                    // Supplied here because this is the only layer that can reach the container.
                    // Keyed by vote id and not merely by post: a thread may embed more than one, and
                    // without the key they would share a single ViewModel and each other's state.
                    voteContent = { voteId ->
                        VoteCard(
                            viewModel =
                            viewModel(
                                key = "vote-$voteId",
                                factory = VoteViewModel.factory(container, voteId),
                            ),
                            onSignIn = { backStack.add(SignInKey) },
                            onUserClick = openSpace,
                        )
                    },
                    // Keyed by payee *and* Ref ID, for the same reason a vote is keyed by its id: one
                    // post may carry several codes, and a shared ViewModel would show one code's
                    // tally under another's amount.
                    stardustContent = { node ->
                        StardustReceiveCard(
                            node = node,
                            viewModel =
                            viewModel(
                                key = "stardust-${node.memberId}-${node.refId}",
                                factory = StardustReceiveViewModel.factory(container, node),
                            ),
                            onSignIn = { backStack.add(SignInKey) },
                        )
                    },
                )
            }

            entry<PostComposerKey> { key ->
                val viewModel: PostComposerViewModel =
                    viewModel(
                        // Keyed by what is being written, or two edits opened in one session would
                        // share the first one's ViewModel — and therefore the first one's text.
                        key = "composer-${key.edit?.commentId ?: "new"}",
                        factory = PostComposerViewModel.factory(container, key.edit),
                    )
                // Whatever this editor is about: the thread when editing, the new-post page otherwise.
                val webUrl =
                    key.edit
                        ?.let { NodeSeekSite.BASE_URL + NodeSeekSite.postPath(it.postId, it.page) }
                        ?: (NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH)
                PostComposerRoute(
                    viewModel = viewModel,
                    onClose = { backStack.removeLastOrNull() },
                    onSignIn = {
                        backStack.add(SignInKey)
                    },
                    onVerify = {
                        backStack.add(WebKey(webUrl, siteTitle, WebViewGoal.CHALLENGE))
                    },
                    onOpenBrowser = {
                        backStack.add(WebKey(webUrl, siteTitle, WebViewGoal.MANAGE))
                    },
                    onPublished = { postId ->
                        backStack.removeLastOrNull()
                        // An edit returns to the thread it came from, which is already underneath —
                        // pushing it again would stack a second copy of the screen being updated.
                        if (key.edit == null) postId?.let { backStack.add(PostDetailKey(it)) }
                    },
                )
            }

            entry<SignInKey> {
                val viewModel: SignInViewModel = viewModel(factory = SignInViewModel.factory(container))
                SignInRoute(
                    viewModel = viewModel,
                    // The widget's web view has to look like the app's other requests, or Cloudflare
                    // is grading a different client than the one that will send the token.
                    userAgent = container.userAgent,
                    onClose = { backStack.removeLastOrNull() },
                    // The screen that asked for a session is underneath; dropping this one puts the
                    // user back on it, and its own reload keys on the session generation.
                    onSignedIn = { backStack.removeLastOrNull() },
                    onUseWebSignIn = {
                        backStack.removeLastOrNull()
                        backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN))
                    },
                    // 忘记密码 and 还没有账号 both land on the site's own sign-in page, on top of this
                    // screen rather than instead of it. Neither has a verified URL of its own, and a
                    // guessed one would be a button that goes nowhere.
                    onOpenSiteSignInPage = {
                        backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN))
                    },
                )
            }

            entry<WebKey> { key ->
                WebViewRoute(
                    url = key.url,
                    title = key.title,
                    goal = key.goal,
                    session = container.sessionRepository,
                    userAgent = container.userAgent,
                    onOpenExternal = openExternalUrl,
                    onClose = { backStack.removeLastOrNull() },
                    // A failed poll means "not yet", never "give up": the page is still open and the
                    // user is still working, so a hiccup on one request must not end the watch.
                    isBound = {
                        runCatchingExceptCancellation {
                            container.accountSettingsRepository.telegramBinding().bound
                        }.getOrDefault(false)
                    },
                )
            }
        }

    /*
     * The entries for one tab's stack, with every "open this somewhere" closure bound to that same
     * stack.
     *
     * Built per stack rather than once for the app because an entry's content lambda is created in a
     * plain (non-composable) function, so whatever it captures is frozen at the moment the entry is
     * built — and `rememberDecoratedNavEntries` only rebuilds entries when *its own* back stack
     * changes. A closure over "whichever tab is current" therefore keeps pointing at whichever tab
     * happened to be current when the entry was made: on a cold start that is the launch tab for all
     * four stacks, and after a rotation or process death it is the restored tab for all four. 访问网站
     * in 我的 pushed its web view onto 首页's stack that way, and nothing appeared to happen.
     *
     * The parameter shadows nothing now — these three used to be declared beside `backStack` above,
     * where they read the `when (currentTab)` result.
     */
    fun destinationProvider(backStack: NavBackStack<NavKey>): (NavKey) -> NavEntry<NavKey> {
        /*
         * Opening a web page, routed by host: nodeseek.com stays in the app's own web view,
         * everything else goes to the browser.
         *
         * This is a cookie decision, not a cosmetic one. A Custom Tab is the browser doing the
         * browsing — its process, and crucially its cookie jar — where this account is not signed
         * in. A site page opened out there shows the user a logged-out stranger's view of their own
         * forum, and a Cloudflare pass earned out there lands in a jar the app cannot read, so the
         * app's own requests keep failing afterwards.
         *
         * By host rather than page by page because "does this one need the session" is a judgement
         * we get wrong: an 内版 thread is an ordinary `/post-` URL right up until it 404s, and the
         * site's own pages move between public and gated without telling us.
         *
         * [WebViewGoal.MANAGE] because there is no cookie to wait for here — the user is done when
         * they say so. The web view carries its own way back out to a real browser; see
         * `WebViewScreen`.
         */
        val openWebUrl: (String) -> Unit = { url ->
            if (NodeSeekSite.isTrustedWebViewUrl(url)) {
                backStack.add(WebKey(url, siteTitle, WebViewGoal.MANAGE))
            } else {
                openExternalUrl(url)
            }
        }

        // Content links: our own post/space/mention URLs get a native screen, and everything else —
        // including the rest of nodeseek.com — goes through the routing above.
        val openSpace: (Long) -> Unit = { uid ->
            backStack.add(UserSpaceKey(uid, isSelf = uid == container.profileRepository.selfUid))
        }
        val openContentUrl: (String) -> Unit = { url ->
            when (val route = NodeSeekSite.parseInternalRoute(url)) {
                is NodeSeekSite.InternalRoute.Post ->
                    backStack.add(PostDetailKey(route.postId, page = route.page))

                is NodeSeekSite.InternalRoute.Space -> openSpace(route.uid)

                is NodeSeekSite.InternalRoute.Member ->
                    // A mention carries only the user name; the uid comes from following the site's
                    // own /member?t= redirect. Any failure (offline, signed out, renamed user) falls
                    // back to the site itself.
                    scope.launch {
                        resolveMemberLink(
                            name = route.name,
                            resolveMemberUid = container.searchRepository::resolveMemberUid,
                            onResolved = openSpace,
                            onFailure = { openWebUrl(url) },
                        )
                    }

                // A link to a notification list is a link to a tab, and a tab is not something a
                // stack can hold — 通知 is where it already lives.
                is NodeSeekSite.InternalRoute.Notifications -> {
                    currentTab = TopLevelDestination.NOTIFICATIONS
                    route.group?.let { notificationsViewModel.selectCategory(it.toCategory()) }
                }

                // The conversation goes on the stack that is open, unlike the tab switch above: a
                // 私信 link inside a thread should leave the thread underneath it.
                is NodeSeekSite.InternalRoute.MessageThread ->
                    backStack.openMessageThread(route.uid)

                null -> openWebUrl(NodeSeekSite.unwrapJumpUrl(url))
            }
        }

        return destinationEntries(backStack, openWebUrl, openSpace, openContentUrl)
    }

    /*
     * One provider per stack, built once.
     *
     * `rememberDecoratedNavEntries` only calls its provider when its own back stack changes, so a
     * provider rebuilt on every recomposition — which is what passing `destinationProvider(stack)`
     * straight through did — was four maps of thirty-five entries built and thrown away each time
     * the unread count ticked. Everything the entries close over is either constant for the life of
     * the composition or a state object read at draw time, so freezing the provider changes nothing
     * they can observe.
     *
     * Four `remember` calls rather than a loop, for the same reason the stacks above are written out.
     */
    val homeProvider =
        remember { stackScopedEntryProvider(TopLevelDestination.HOME, destinationProvider(homeStack)) }
    val searchProvider =
        remember { stackScopedEntryProvider(TopLevelDestination.SEARCH, destinationProvider(searchStack)) }
    val notificationsProvider =
        remember {
            stackScopedEntryProvider(
                TopLevelDestination.NOTIFICATIONS,
                destinationProvider(notificationsStack),
            )
        }
    val profileProvider =
        remember { stackScopedEntryProvider(TopLevelDestination.PROFILE, destinationProvider(profileStack)) }

    val viewModelDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val homeEntries =
        rememberDecoratedNavEntries(
            backStack = homeStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider = homeProvider,
        )
    val searchEntries =
        rememberDecoratedNavEntries(
            backStack = searchStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider = searchProvider,
        )
    val notificationEntries =
        rememberDecoratedNavEntries(
            backStack = notificationsStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider = notificationsProvider,
        )
    val profileEntries =
        rememberDecoratedNavEntries(
            backStack = profileStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider = profileProvider,
        )
    val tabEntries =
        when (currentTab) {
            TopLevelDestination.HOME -> homeEntries
            TopLevelDestination.SEARCH -> searchEntries
            TopLevelDestination.NOTIFICATIONS -> notificationEntries
            TopLevelDestination.PROFILE -> profileEntries
        }

    /*
     * "Exit through home": 首页's entries sit under every other tab's, so the user always leaves the
     * app through 首页.
     *
     * This used to be a `BackHandler` that flipped `currentTab`, which was correct but silent — the
     * gesture had nothing to preview, so backing out of 搜索 was a hard cut. Handing `NavDisplay` a
     * stack that really does have 首页 underneath makes it an ordinary pop, which means it animates
     * and the predictive-back gesture shows where it is going.
     *
     * Safe only because each tab's panes carry their own scene key — see [paneMetadataOf].
     * `ListDetailSceneStrategy` collects the run of panes at the top of the list, and without the
     * key it would have swept 首页's list into 搜索's scene and drawn 首页's empty-detail text beside
     * the search results.
     */
    val entries =
        if (currentTab == TopLevelDestination.HOME) tabEntries else homeEntries + tabEntries

    /*
     * 启动提醒 lives here rather than on any screen: the launch check answers while whatever tab was
     * last open is drawing, and a dialog owned by a screen would be missed by everyone who does not
     * happen to be on that screen. Read straight off the shared updater — the same instance 关于 uses,
     * so 下载并安装 hands the download to a screen that is already watching it.
     */
    val updateReminder by container.appUpdateRepository.launchReminder.collectAsStateWithLifecycle()
    updateReminder?.let { release ->
        UpdateReminderDialog(
            release = release,
            onDownload = {
                container.appUpdateRepository.acceptLaunchReminder()
                // Onto the current tab's stack, so Back returns to whatever the reminder interrupted.
                backStack.add(AboutAppKey)
            },
            onPostpone = container.appUpdateRepository::postponeLaunchReminder,
        )
    }

    NavigationSuiteScaffold(
        navigationItems = {
            NodysseyNavigationItems(
                current = currentTab,
                onSelect = { destination ->
                    // Re-selecting a tab scrolls its list back to the start — but only the two tabs
                    // that *are* a list long enough to get lost in. 搜索 and 我的 stay inert, because
                    // a tab that silently jumps somewhere is worse than one that does nothing.
                    if (destination == currentTab) {
                        when (destination) {
                            TopLevelDestination.HOME -> homeScrollToTopRequests++
                            TopLevelDestination.NOTIFICATIONS -> notificationsScrollToTopRequests++
                            else -> Unit
                        }
                    }
                    currentTab = destination
                },
                unreadCount = notificationsState.counts.all,
            )
        },
        modifier = modifier,
        navigationSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(windowAdaptiveInfo),
        state = navigationSuiteState,
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            /*
             * Withheld on a two-pane window, where a row and the thread it opens are on screen at
             * once and a single shared-element key would have two live claims on it. Provided as a
             * composition local rather than threaded through every screen: the two ends of the
             * flight are a feed row and a thread header, twelve composables apart, and the only
             * thing they need to agree on is that the flight is happening at all.
             */
            CompositionLocalProvider(
                LocalThreadTransition provides
                    this@SharedTransitionLayout.takeUnless { isListDetailExpanded },
            ) {
                NavDisplay(
                    entries = entries,
                    // The current tab first, and only when it is spent does back mean "leave this
                    // tab". `NavDisplay` handles back whenever there is more than one entry, and
                    // with 首页 underneath there always is — so popping blindly would empty a
                    // secondary tab's stack.
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        } else {
                            currentTab = TopLevelDestination.HOME
                        }
                    },
                    sceneStrategies = listOf(listDetailSceneStrategy),
                    sharedTransitionScope = this@SharedTransitionLayout,
                )
            }
        }
    }
}

/** Resolves a member-name link without turning coroutine cancellation into browser navigation. */
internal suspend fun resolveMemberLink(
    name: String,
    resolveMemberUid: suspend (String) -> Long?,
    onResolved: (Long) -> Unit,
    onFailure: () -> Unit,
) {
    val uid = runCatchingExceptCancellation { resolveMemberUid(name) }.getOrNull()
    if (uid != null) onResolved(uid) else onFailure()
}

@Composable
private fun EmptyDetailPane(text: StringResource) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Which half of a list-detail layout a destination is drawn in, or null if it takes the whole window.
 *
 * The single source of truth for that question. Two things read it, and they used to be two separate
 * statements of the same fact: [paneMetadataOf] turns it into what [ListDetailSceneStrategy] reads to
 * build a scene, and the app reads it directly to decide whether a detail still needs its own back
 * arrow and whether the navigation area would be covering a list or sitting beside one. Those could
 * disagree — a `listPane` on an entry whose key was missing here left a thread with a back arrow
 * pointing at a list already on screen — so now the entries carry no pane metadata of their own and
 * [stackScopedEntryProvider] derives it from here. Adding a destination is one edit, and a test can
 * read the answer back.
 */
internal enum class PaneRole {
    LIST,
    DETAIL,
}

internal fun paneRoleOf(key: NavKey?): PaneRole? =
    when (key) {
        // Every tab root that is a list of things worth opening one of. 我的 is not one: it is a menu
        // whose rows are settings pages, and a settings page is not a detail.
        PostListKey, SearchKey, NotificationsKey -> PaneRole.LIST

        // A user's space is a list wherever it is reached from — including on top of a thread, where
        // tapping an author then leaves their posts beside the one being read.
        is UserSpaceKey -> PaneRole.LIST

        is PostDetailKey, is MessageThreadKey -> PaneRole.DETAIL

        else -> null
    }

/**
 * Whether a list is sharing the window with whatever is on top of this stack.
 *
 * Mirrors [ListDetailSceneStrategy.calculateScene]: a scene is built from the run of pane-carrying
 * entries at the top of the stack, and that run ends at the first entry carrying no pane role. So a
 * thread opened from 浏览历史 is full-screen and keeps its back arrow — 浏览历史 is not a pane — while
 * the same thread opened from the feed, from search or from a user's space does not.
 *
 * Only meaningful once the window is wide enough for two panes; every caller checks that first.
 */
internal fun List<NavKey>.showsListPane(): Boolean =
    asReversed()
        .takeWhile { paneRoleOf(it) != null }
        .any { paneRoleOf(it) == PaneRole.LIST }

/**
 * The pane metadata for a destination, derived from [paneRoleOf].
 *
 * The placeholder is resolved here rather than inside the lambda so that a list pane added without a
 * matching line in [emptyDetailTextOf] fails when the key is pushed, not when a wide window happens
 * to draw the empty half of it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun paneMetadataOf(key: NavKey, destination: TopLevelDestination): Map<String, Any> =
    when (paneRoleOf(key)) {
        PaneRole.LIST -> {
            val text = emptyDetailTextOf(key)
            ListDetailSceneStrategy.listPane(
                destination,
                detailPlaceholder = { EmptyDetailPane(text) },
            )
        }

        PaneRole.DETAIL -> ListDetailSceneStrategy.detailPane(destination)

        null -> emptyMap()
    }

/** What the detail half says on a wide window before anything has been opened in it. */
internal fun emptyDetailTextOf(key: NavKey): StringResource =
    when (key) {
        PostListKey -> Res.string.home_pane_empty
        SearchKey -> Res.string.search_pane_empty
        NotificationsKey -> Res.string.notifications_pane_empty
        is UserSpaceKey -> Res.string.space_pane_empty
        else -> error("$key is a list pane with nothing to say when its detail is empty")
    }

/**
 * What a thread opened from a list row animates as. See the `entry<PostDetailKey>` metadata for why
 * this is not the default slide, and [io.github.nodyssey.ui.common.sharedThreadTitle] for the thing
 * the fade is clearing the way for.
 */
private val ThreadOpenTransition =
    NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.predictivePopTransitionSpec { _ -> fadeIn() togetherWith fadeOut() }

private fun stackScopedEntryProvider(
    destination: TopLevelDestination,
    provider: (NavKey) -> NavEntry<NavKey>,
): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = provider(key)
    NavEntry(
        key = key,
        contentKey = "${destination.name}:${entry.contentKey}",
        metadata = entry.metadata + paneMetadataOf(key, destination),
    ) {
        entry.Content()
    }
}

/** Buying an invite code is a site-side spend; the app only confirms it. */
private fun inviteWebKey(title: String): WebKey =
    WebKey(NodeSeekSite.BASE_URL + NodeSeekSite.INVITE_PATH, title, WebViewGoal.MANAGE)

/**
 * Opens a 私信 conversation, unless it is already the screen on top.
 *
 * The guard is for the notification tap and the deep link, which can both arrive again while the
 * conversation they name is open — a second copy would only give Back something to undo.
 *
 * The name is left blank because a URL does not carry one: `MessageThreadViewModel` fills it in from
 * the thread it loads, and the app bar falls back to 私信 until then.
 */
private fun NavBackStack<NavKey>.openMessageThread(uid: Long) {
    if ((lastOrNull() as? MessageThreadKey)?.uid == uid) return
    add(MessageThreadKey(uid, userName = ""))
}

private fun NodeSeekSite.NotificationGroup.toCategory(): NotificationCategory =
    when (this) {
        NodeSeekSite.NotificationGroup.MENTIONS -> NotificationCategory.MENTIONS
        NodeSeekSite.NotificationGroup.MESSAGES -> NotificationCategory.MESSAGES
    }

private fun imageViewerKeyFor(urls: List<String>, url: String): ImageViewerKey =
    ImageViewerKey(urls = urls, index = urls.indexOf(url).coerceAtLeast(0))
