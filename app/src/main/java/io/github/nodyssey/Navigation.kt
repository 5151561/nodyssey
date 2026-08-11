package io.github.nodyssey

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.account.AccountSettingsRoute
import io.github.nodyssey.ui.account.AccountSettingsViewModel
import io.github.nodyssey.ui.account.BlockListRoute
import io.github.nodyssey.ui.account.BlockListViewModel
import io.github.nodyssey.ui.account.ContactRoute
import io.github.nodyssey.ui.account.ContactViewModel
import io.github.nodyssey.ui.account.NodeImageRoute
import io.github.nodyssey.ui.account.NodeImageViewModel
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
import io.github.nodyssey.ui.composer.PostComposerRoute
import io.github.nodyssey.ui.composer.PostComposerViewModel
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.history.ReadHistoryRoute
import io.github.nodyssey.ui.history.ReadHistoryViewModel
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
import io.github.nodyssey.ui.search.SearchRoute
import io.github.nodyssey.ui.search.SearchViewModel
import io.github.nodyssey.ui.settings.AboutAppRoute
import io.github.nodyssey.ui.settings.AboutAppViewModel
import io.github.nodyssey.ui.settings.AboutCommunityScreen
import io.github.nodyssey.ui.settings.AppLinks
import io.github.nodyssey.ui.settings.ChangelogScreen
import io.github.nodyssey.ui.settings.CommunityLinks
import io.github.nodyssey.ui.settings.NotificationSettingsRoute
import io.github.nodyssey.ui.settings.NotificationSettingsViewModel
import io.github.nodyssey.ui.settings.OpenSourceLicensesScreen
import io.github.nodyssey.ui.settings.PrivacyRoute
import io.github.nodyssey.ui.settings.PrivacyViewModel
import io.github.nodyssey.ui.settings.SettingsRoute
import io.github.nodyssey.ui.settings.SettingsViewModel
import io.github.nodyssey.ui.settings.UpdateReminderDialog
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
import io.github.nodyssey.ui.vote.VoteCard
import io.github.nodyssey.ui.vote.VoteViewModel
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainNavigation(
    container: AppContainer,
    modifier: Modifier = Modifier,
    initialTab: TopLevelDestination = TopLevelDestination.HOME,
) {
    val signInUrl = NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH

    // Hoisted out of the navigation lambdas below, which are not composable.
    val siteTitle = stringResource(R.string.app_name)
    val aboutSiteTitle = stringResource(R.string.about_site)
    val privacyTitle = stringResource(R.string.about_privacy)
    val rssLabel = stringResource(R.string.about_rss)
    val uriHandler = LocalUriHandler.current
    val openExternalUrl: (String) -> Unit = remember(uriHandler) {
        { url ->
            if (NodeSeekSite.isExternalWebUrl(url)) {
                runCatching { uriHandler.openUri(url) }
            }
        }
    }
    val notificationsViewModel: NotificationsViewModel =
        viewModel(factory = NotificationsViewModel.factory(container))
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()

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
    val homeStack = rememberNavBackStack(PostListKey)
    val searchStack = rememberNavBackStack(SearchKey)
    val notificationsStack = rememberNavBackStack(NotificationsKey)
    val profileStack = rememberNavBackStack(ProfileKey)

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
     * was the one place in the bar where a tap did nothing at all.
     *
     * A counter rather than a boolean flag: two taps in a row are two separate requests, and a flag
     * would need clearing afterwards — which is a second write the screen would have to own. Saved
     * rather than merely remembered, so that it survives a rotation alongside the screen's record of
     * which request it has already answered; a counter that reset while that record did not would
     * look like a fresh tap and scroll a restored list back to the top.
     */
    var homeScrollToTopRequests by rememberSaveable { mutableIntStateOf(0) }

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
    val atTabRoot = TopLevelDestination.forKey(backStack.lastOrNull()) != null
    val showNavigationSuite =
        (atTabRoot && (!tabBarHiddenByScroll || isListDetailExpanded)) ||
            (
                currentTab == TopLevelDestination.HOME &&
                    isListDetailExpanded &&
                    backStack.lastOrNull() is PostDetailKey
                )
    val navigationSuiteState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(showNavigationSuite) {
        if (showNavigationSuite) navigationSuiteState.show() else navigationSuiteState.hide()
    }

    // Content links only: our own post/space/mention URLs stay in the app; everything else leaves
    // it. Explicit "open in browser" actions keep openExternalUrl, or they would loop back here.
    val scope = rememberCoroutineScope()
    val openSpace: (Long) -> Unit = { uid ->
        backStack.add(UserSpaceKey(uid, isSelf = uid == container.profileRepository.selfUid))
    }
    val openContentUrl: (String) -> Unit = { url ->
        when (val route = NodeSeekSite.parseInternalRoute(url)) {
            is NodeSeekSite.InternalRoute.Post ->
                backStack.add(PostDetailKey(route.postId, page = route.page))

            is NodeSeekSite.InternalRoute.Space -> openSpace(route.uid)

            is NodeSeekSite.InternalRoute.Member ->
                // A mention carries only the user name; the uid comes from the member-search API.
                // Any failure (offline, signed out, renamed user) falls back to the site itself.
                scope.launch {
                    resolveMemberLink(
                        name = route.name,
                        searchUsers = container.searchRepository::searchUsers,
                        onResolved = openSpace,
                        onFailure = { openExternalUrl(url) },
                    )
                }

            null -> openExternalUrl(NodeSeekSite.unwrapJumpUrl(url))
        }
    }

    /*
     * Back out of a secondary tab returns to home rather than leaving the app.
     *
     * `NavDisplay` only handles back while the current stack has something to pop, so without this
     * the first back press on a tab root would exit — and now that the tabs keep their stacks,
     * exiting from "我的" would silently drop three other stacks the user could still see.
     */
    BackHandler(enabled = atTabRoot && currentTab != TopLevelDestination.HOME) {
        currentTab = TopLevelDestination.HOME
    }

    fun destinationProvider(backStack: NavBackStack<NavKey>) =
        entryProvider<NavKey> {
            entry<PostListKey>(
                metadata =
                ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { EmptyDetailPane() },
                ),
            ) {
                val viewModel: PostListViewModel =
                    viewModel(factory = PostListViewModel.factory(container))
                PostListRoute(
                    viewModel = viewModel,
                    listState = homeListState,
                    onPostClick = { backStack.add(PostDetailKey(it)) },
                    onCreatePost = { backStack.add(PostComposerKey) },
                    onSignIn = {
                        backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN))
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
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                    onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                    onNavigationBarHiddenChanged = { hidden ->
                        if (!currentListDetailExpanded) tabBarHiddenByScroll = hidden
                    },
                )
            }

            entry<NotificationsKey> {
                NotificationsRoute(
                    viewModel = notificationsViewModel,
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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
                    onBack = { backStack.removeLastOrNull() },
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH,
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                    onOpenBrowser = openExternalUrl,
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
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                    onSettings = { backStack.add(SettingsKey) },
                    hasAppUpdate = updateState.available != null,
                    onAccountSettings = { backStack.add(AccountSettingsKey) },
                    onOpenWebsite = { openExternalUrl(NodeSeekSite.BASE_URL) },
                    onOpenSpace = { uid -> backStack.add(UserSpaceKey(uid, isSelf = true)) },
                    // 我的收藏 is the space page's own 收藏 tab, opened directly. A separate screen
                    // would be the same list rendered twice from the same endpoint.
                    onCollections = { uid ->
                        backStack.add(UserSpaceKey(uid, isSelf = true, openCollections = true))
                    },
                    onHistory = { backStack.add(ReadHistoryKey) },
                    onAssets = { backStack.add(AssetsKey()) },
                    onAttendance = { backStack.add(AssetsKey(openAttendanceChooser = true)) },
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
                    onOpenNotifications = { backStack.add(NotificationSettingsKey) },
                    onOpenAbout = { backStack.add(AboutAppKey) },
                    onOpenLicenses = { backStack.add(OpenSourceLicensesKey) },
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
                val context = LocalContext.current
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
                    onCopyRss = {
                        context
                            .getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(
                                ClipData.newPlainText(
                                    rssLabel,
                                    CommunityLinks.RSS,
                                ),
                            )
                    },
                )
            }

            entry<PrivacyKey> {
                val privacyViewModel: PrivacyViewModel =
                    viewModel(factory = PrivacyViewModel.factory(container))
                PrivacyRoute(
                    viewModel = privacyViewModel,
                    onBack = { backStack.removeLastOrNull() },
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
                ChangelogScreen(
                    versionName = container.appVersion.name.ifBlank { "—" },
                    onBack = { backStack.removeLastOrNull() },
                    onOpenReleases = { openExternalUrl(AppLinks.RELEASES) },
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
                    // 私信 is the site's own conversation page: authenticated, and with a Markdown
                    // composer we have not reimplemented, so it opens in the session's web view.
                    onMessage = { uid ->
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.messagePath(uid),
                                siteTitle,
                                WebViewGoal.MANAGE,
                            ),
                        )
                    },
                    // 空间页右上角的笔是「编辑资料」，直接进资料编辑页，不是账号设置的目录页。
                    onEditProfile = { backStack.add(AccountProfileFieldsKey) },
                    onOpenBrowser = openExternalUrl,
                    onLinkClick = openContentUrl,
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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
                    onOpenBrowser = { url ->
                        backStack.add(WebKey(url, siteTitle, WebViewGoal.MANAGE))
                    },
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                )
            }

            entry<AssetsKey> { key ->
                val viewModel: AssetsViewModel =
                    viewModel(factory = AssetsViewModel.factory(container))
                AssetsRoute(
                    viewModel = viewModel,
                    openAttendanceChooser = key.openAttendanceChooser,
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
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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
                    onOpenBrowser = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.CREDIT_PATH,
                                siteTitle,
                                WebViewGoal.MANAGE,
                            ),
                        )
                    },
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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
                    onOpenNodeImage = { backStack.add(AccountNodeImageKey) },
                )
            }

            entry<AccountNodeImageKey> {
                val viewModel: NodeImageViewModel =
                    viewModel(factory = NodeImageViewModel.factory(container))
                NodeImageRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    // nodeimage.com is a different site with a different session; the in-app web view
                    // exists to carry NodeSeek's cookies and has no business holding these.
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                )
            }

            entry<AccountProfileFieldsKey> {
                val viewModel: ProfileFieldsViewModel =
                    viewModel(factory = ProfileFieldsViewModel.factory(container))
                ProfileFieldsRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
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
                )
            }

            entry<AccountBlockListKey> {
                val viewModel: BlockListViewModel =
                    viewModel(factory = BlockListViewModel.factory(container))
                BlockListRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<AccountPreferencesKey> {
                val viewModel: PreferencesViewModel =
                    viewModel(factory = PreferencesViewModel.factory(container))
                PreferencesRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<CommunityToolsKey> {
                CommunityToolsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onAward = { backStack.add(AwardKey) },
                    onProviders = { openExternalUrl(NodeSeekSite.BASE_URL + NodeSeekSite.PROVIDERS_PATH) },
                    onFriends = { openExternalUrl(NodeSeekSite.BASE_URL + NodeSeekSite.FRIENDS_PATH) },
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
                    onOpenBrowser = openExternalUrl,
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                )
            }

            entry<LuckyKey> {
                val viewModel: LuckyViewModel =
                    viewModel(factory = LuckyViewModel.factory(container))
                LuckyRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBrowser = openExternalUrl,
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
                    onOpenBrowser = openExternalUrl,
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                )
            }

            entry<ImageViewerKey> { key ->
                val context = LocalContext.current
                val viewModel: ImageViewerViewModel =
                    viewModel(factory = ImageViewerViewModel.factory(container, context))
                val saveOutcome by viewModel.saveOutcome.collectAsStateWithLifecycle()
                ImageViewerScreen(
                    urls = key.urls,
                    initialIndex = key.index,
                    onClose = { backStack.removeLastOrNull() },
                    onOpenBrowser = openExternalUrl,
                    saveOutcome = saveOutcome,
                    onSave = viewModel::save,
                )
            }

            entry<PostDetailKey>(
                metadata = ListDetailSceneStrategy.detailPane(),
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
                    showBackButton =
                    !(currentListDetailExpanded && backStack.firstOrNull() == PostListKey),
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBrowser = openExternalUrl,
                    onLinkClick = openContentUrl,
                    onAuthorClick = openSpace,
                    onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                    onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                    onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
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
                            onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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
                            onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                        )
                    },
                )
            }

            entry<PostComposerKey> {
                val viewModel: PostComposerViewModel =
                    viewModel(factory = PostComposerViewModel.factory(container))
                PostComposerRoute(
                    viewModel = viewModel,
                    onClose = { backStack.removeLastOrNull() },
                    onSignIn = {
                        backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN))
                    },
                    onVerify = {
                        backStack.add(
                            WebKey(
                                NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH,
                                siteTitle,
                                WebViewGoal.CHALLENGE,
                            ),
                        )
                    },
                    onPublished = { postId ->
                        backStack.removeLastOrNull()
                        postId?.let { backStack.add(PostDetailKey(it)) }
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

    val viewModelDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val homeEntries =
        rememberDecoratedNavEntries(
            backStack = homeStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider =
            stackScopedEntryProvider(
                TopLevelDestination.HOME,
                destinationProvider(homeStack),
            ),
        )
    val searchEntries =
        rememberDecoratedNavEntries(
            backStack = searchStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider =
            stackScopedEntryProvider(
                TopLevelDestination.SEARCH,
                destinationProvider(searchStack),
            ),
        )
    val notificationEntries =
        rememberDecoratedNavEntries(
            backStack = notificationsStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider =
            stackScopedEntryProvider(
                TopLevelDestination.NOTIFICATIONS,
                destinationProvider(notificationsStack),
            ),
        )
    val profileEntries =
        rememberDecoratedNavEntries(
            backStack = profileStack,
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                viewModelDecorator,
            ),
            entryProvider =
            stackScopedEntryProvider(
                TopLevelDestination.PROFILE,
                destinationProvider(profileStack),
            ),
        )
    val entries =
        when (currentTab) {
            TopLevelDestination.HOME -> homeEntries
            TopLevelDestination.SEARCH -> searchEntries
            TopLevelDestination.NOTIFICATIONS -> notificationEntries
            TopLevelDestination.PROFILE -> profileEntries
        }

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
                    // Re-selecting 首页 scrolls the feed back to the start. Only 首页 — the other
                    // three are not lists you get lost in, and a tab that silently jumps somewhere
                    // is worse than one that does nothing.
                    if (destination == currentTab && destination == TopLevelDestination.HOME) {
                        homeScrollToTopRequests++
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
        NavDisplay(
            entries = entries,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(listDetailSceneStrategy),
        )
    }
}

/** Resolves a member-name link without turning coroutine cancellation into browser navigation. */
internal suspend fun resolveMemberLink(
    name: String,
    searchUsers: suspend (String) -> List<UserSearchResult>,
    onResolved: (Long) -> Unit,
    onFailure: () -> Unit,
) {
    val uid =
        runCatchingExceptCancellation { searchUsers(name) }
            .getOrNull()
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.uid
    if (uid != null) onResolved(uid) else onFailure()
}

@Composable
private fun EmptyDetailPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.home_pane_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stackScopedEntryProvider(
    destination: TopLevelDestination,
    provider: (NavKey) -> NavEntry<NavKey>,
): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = provider(key)
    NavEntry(
        key = key,
        contentKey = "${destination.name}:${entry.contentKey}",
        metadata = entry.metadata,
    ) {
        entry.Content()
    }
}

/** Buying an invite code is a site-side spend; the app only confirms it. */
private fun inviteWebKey(title: String): WebKey =
    WebKey(NodeSeekSite.BASE_URL + NodeSeekSite.INVITE_PATH, title, WebViewGoal.MANAGE)

private fun imageViewerKeyFor(urls: List<String>, url: String): ImageViewerKey =
    ImageViewerKey(urls = urls, index = urls.indexOf(url).coerceAtLeast(0))
