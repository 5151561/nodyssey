package io.github.nsreader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.account.AccountSettingsRoute
import io.github.nsreader.ui.account.AccountSettingsViewModel
import io.github.nsreader.ui.account.ContactBlockRoute
import io.github.nsreader.ui.account.ContactBlockViewModel
import io.github.nsreader.ui.account.HomeBoardsRoute
import io.github.nsreader.ui.account.HomeBoardsViewModel
import io.github.nsreader.ui.account.ProfileFieldsRoute
import io.github.nsreader.ui.account.ProfileFieldsViewModel
import io.github.nsreader.ui.account.SecurityRoute
import io.github.nsreader.ui.account.SecurityViewModel
import io.github.nsreader.ui.assets.AssetsRoute
import io.github.nsreader.ui.assets.AssetsViewModel
import io.github.nsreader.ui.assets.StardustRoute
import io.github.nsreader.ui.assets.StardustViewModel
import io.github.nsreader.ui.composer.PostComposerRoute
import io.github.nsreader.ui.composer.PostComposerViewModel
import io.github.nsreader.ui.composer.ReplyComposerViewModel
import io.github.nsreader.ui.login.WebViewGoal
import io.github.nsreader.ui.login.WebViewRoute
import io.github.nsreader.ui.messages.MessageThreadRoute
import io.github.nsreader.ui.messages.MessageThreadViewModel
import io.github.nsreader.ui.navigation.NodeSeekNavigationItems
import io.github.nsreader.ui.navigation.TopLevelDestination
import io.github.nsreader.ui.notifications.NotificationsRoute
import io.github.nsreader.ui.notifications.NotificationsViewModel
import io.github.nsreader.ui.postdetail.PostDetailRoute
import io.github.nsreader.ui.postdetail.PostDetailViewModel
import io.github.nsreader.ui.postlist.PostListRoute
import io.github.nsreader.ui.postlist.PostListViewModel
import io.github.nsreader.ui.profile.ProfileRoute
import io.github.nsreader.ui.profile.ProfileViewModel
import io.github.nsreader.ui.search.SearchRoute
import io.github.nsreader.ui.search.SearchViewModel
import io.github.nsreader.ui.settings.SettingsRoute
import io.github.nsreader.ui.settings.SettingsViewModel
import io.github.nsreader.ui.space.FollowRoute
import io.github.nsreader.ui.space.FollowViewModel
import io.github.nsreader.ui.space.UserSpaceRoute
import io.github.nsreader.ui.space.UserSpaceViewModel
import io.github.nsreader.ui.tools.AwardRoute
import io.github.nsreader.ui.tools.AwardViewModel
import io.github.nsreader.ui.tools.CommunityToolsScreen
import io.github.nsreader.ui.tools.InviteRoute
import io.github.nsreader.ui.tools.InviteViewModel
import io.github.nsreader.ui.tools.LuckyRoute
import io.github.nsreader.ui.tools.LuckyViewModel
import io.github.nsreader.ui.tools.RulingRoute
import io.github.nsreader.ui.tools.RulingViewModel
import io.github.nsreader.ui.viewer.ImageViewerScreen
import io.github.nsreader.ui.viewer.ImageViewerViewModel

@Composable
fun MainNavigation(container: AppContainer) {
    val signInUrl = NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH

    // Hoisted out of the navigation lambdas below, which are not composable.
    val siteTitle = stringResource(R.string.app_name)
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

    var currentTab by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }

    // Transient by design: a fling should tuck the bar away, not a configuration change keep it away.
    var feedScrollActive by remember { mutableStateOf(false) }

    val backStack: NavBackStack<NavKey> =
        when (currentTab) {
            TopLevelDestination.HOME -> homeStack
            TopLevelDestination.SEARCH -> searchStack
            TopLevelDestination.NOTIFICATIONS -> notificationsStack
            TopLevelDestination.PROFILE -> profileStack
        }

    // The bar belongs to the top-level destinations only. A thread, the image viewer and the web
    // view are full-screen by design — showing a tab bar under them would invite leaving mid-read.
    val atTabRoot = TopLevelDestination.forKey(backStack.lastOrNull()) != null

    // Content links only: our own post/space/mention URLs stay in the app; everything else leaves
    // it. Explicit "open in browser" actions keep openExternalUrl, or they would loop back here.
    val scope = rememberCoroutineScope()
    val openSpace: (Long) -> Unit = { uid ->
        backStack.add(UserSpaceKey(uid, isSelf = uid == container.profileRepository.selfUid))
    }
    val openContentUrl: (String) -> Unit = { url ->
        when (val route = NodeSeekSite.parseInternalRoute(url)) {
            is NodeSeekSite.InternalRoute.Post -> backStack.add(PostDetailKey(route.postId))
            is NodeSeekSite.InternalRoute.Space -> openSpace(route.uid)
            is NodeSeekSite.InternalRoute.Member ->
                // A mention carries only the user name; the uid comes from the member-search API.
                // Any failure (offline, signed out, renamed user) falls back to the site itself.
                scope.launch {
                    val uid =
                        runCatching { container.searchRepository.searchUsers(route.name) }
                            .getOrNull()
                            ?.firstOrNull { it.name.equals(route.name, ignoreCase = true) }
                            ?.uid
                    if (uid != null) openSpace(uid) else openExternalUrl(url)
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

    NavigationSuiteScaffold(
        navigationItems = {
            NodeSeekNavigationItems(
                current = currentTab,
                onSelect = { destination -> currentTab = destination },
                unreadCount = notificationsState.counts.all,
            )
        },
        /*
         * `None` is how this component hides: the layout stops reserving space for navigation
         * instead of drawing an empty bar. Everywhere else the type comes from the real window,
         * so the bar becomes a rail on a tablet or an unfolded foldable — which targetSdk 36 makes
         * a requirement rather than a nicety, since large screens can no longer be told to stay
         * phone-shaped.
         */
        navigationSuiteType =
        if (atTabRoot && !feedScrollActive) {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
        } else {
            NavigationSuiteType.None
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            /*
             * The default is `listOf(rememberSaveableStateHolderNavEntryDecorator())` and nothing
             * else, which leaves `viewModel()` resolving against the Activity's store: every
             * PostDetailViewModel ever opened stays alive for the life of the process, each one
             * holding a parsed comment tree, and `onCleared` never runs. The ViewModel decorator is
             * what scopes them to the entry — note that naming it replaces the default, so the
             * SaveableStateHolder one has to be listed again or the scroll positions go with it.
             */
            entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider =
            entryProvider {
                entry<PostListKey> {
                    val viewModel: PostListViewModel =
                        viewModel(factory = PostListViewModel.factory(container))
                    HomePane(
                        container = container,
                        listViewModel = viewModel,
                        onOpenPostFullScreen = { backStack.add(PostDetailKey(it)) },
                        onCreatePost = { backStack.add(PostComposerKey) },
                        onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                        onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                        onOpenBrowser = openExternalUrl,
                        onLinkClick = openContentUrl,
                        onAuthorClick = openSpace,
                        onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
                        onFeedScrollActiveChanged = { feedScrollActive = it },
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
                    ProfileRoute(
                        viewModel = viewModel,
                        onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                        onSettings = { backStack.add(SettingsKey) },
                        onAccountSettings = { backStack.add(AccountSettingsKey) },
                        onOpenWebsite = { openExternalUrl(NodeSeekSite.BASE_URL) },
                        onOpenSpace = { uid -> backStack.add(UserSpaceKey(uid, isSelf = true)) },
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
                    )
                }

                entry<UserSpaceKey> { key ->
                    val viewModel: UserSpaceViewModel =
                        viewModel(
                            key = "space-${key.uid}",
                            factory = UserSpaceViewModel.factory(container, key.uid, key.isSelf),
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
                        onEditProfile = { backStack.add(AccountSettingsKey) },
                        onOpenBrowser = openExternalUrl,
                        onLinkClick = openContentUrl,
                        onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
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

                entry<AssetsKey> {
                    val viewModel: AssetsViewModel =
                        viewModel(factory = AssetsViewModel.factory(container))
                    AssetsRoute(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                        // The chicken ledger has no board of its own in this batch and no endpoint
                        // behind it; the site's table is the honest destination.
                        onChickenLedger = {
                            backStack.add(
                                WebKey(
                                    NodeSeekSite.BASE_URL + NodeSeekSite.CREDIT_PATH,
                                    siteTitle,
                                    WebViewGoal.MANAGE,
                                ),
                            )
                        },
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

                entry<StardustKey> {
                    val viewModel: StardustViewModel =
                        viewModel(factory = StardustViewModel.factory(container))
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    // The ledger URL is per-member, so it exists only once the profile call has said
                    // who we are. The screen keeps the transfer entry off until then — landing a
                    // just-confirmed transfer on the home page would silently drop the typed form.
                    val ledgerUrl = state.uid?.let { NodeSeekSite.BASE_URL + NodeSeekSite.stardustPath(it) }
                    val openLedger = {
                        backStack.add(
                            WebKey(ledgerUrl ?: NodeSeekSite.BASE_URL, siteTitle, WebViewGoal.MANAGE),
                        )
                        Unit
                    }
                    StardustRoute(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                        // The ledger needs the session's cookies, so it opens in-app like every other
                        // authenticated page rather than in the cookie-less system browser.
                        onOpenBrowser = openLedger,
                        // Confirmed in the app, submitted on the site: sending stardust is irreversible
                        // and the endpoint is undocumented, so the last step stays where it works.
                        onTransferOnSite = openLedger,
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
                        onOpenContactAndBlock = { backStack.add(AccountContactBlockKey) },
                        // 常用偏好 is this app's own settings screen rather than a copy of it: the site's
                        // `#preference` group covers the website's layout, which the app does not render.
                        onOpenDisplayPreferences = { backStack.add(SettingsKey) },
                        onOpenHomeBoards = { backStack.add(HomeBoardsKey) },
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

                entry<AccountContactBlockKey> {
                    val viewModel: ContactBlockViewModel =
                        viewModel(factory = ContactBlockViewModel.factory(container))
                    ContactBlockRoute(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }

                entry<HomeBoardsKey> {
                    val viewModel: HomeBoardsViewModel =
                        viewModel(factory = HomeBoardsViewModel.factory(container))
                    HomeBoardsRoute(
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

                entry<PostDetailKey> { key ->
                    // Keyed so navigating to a different post builds a fresh ViewModel.
                    val viewModel: PostDetailViewModel =
                        viewModel(
                            key = "post-${key.postId}",
                            factory = PostDetailViewModel.factory(container, key.postId),
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
                        initialFloor = key.floor,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenBrowser = openExternalUrl,
                        onLinkClick = openContentUrl,
                        onAuthorClick = openSpace,
                        onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                        onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                        onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
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
                    )
                }
            },
        )
    }
}

/**
 * The home tab: one column on a phone, list and thread side by side once the window can hold both.
 *
 * Above 600dp the thread stops being a separate destination and becomes the right pane, which is what
 * makes a tablet or an unfolded foldable worth the extra width — you keep your place in the list while
 * reading, and the rail stays reachable. Below it, nothing changes: tapping a row pushes the full-screen
 * thread exactly as before.
 *
 * The width is read from the actual space this pane was given rather than from the window, so it is
 * still correct in multi-window and in a preview.
 */
@Composable
private fun HomePane(
    container: AppContainer,
    listViewModel: PostListViewModel,
    onOpenPostFullScreen: (Long) -> Unit,
    onCreatePost: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (List<String>, String) -> Unit,
    onLinkClick: (String) -> Unit = onOpenBrowser,
    onAuthorClick: (Long) -> Unit = {},
    /** Phone layout only: the tablet's rail sits beside the list, not under the thumb. */
    onFeedScrollActiveChanged: (Boolean) -> Unit = {},
) {
    BoxWithConstraints {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
        val listPaneWidth = if (maxWidth >= WIDE_WINDOW_WIDTH) WIDE_LIST_PANE_WIDTH else LIST_PANE_WIDTH

        // Survives rotation, and survives switching tabs and coming back, because the whole entry does.
        var selectedPostId by rememberSaveable { mutableStateOf<Long?>(null) }

        if (!twoPane) {
            /*
             * A selection made in the two-pane layout follows the user through a rotation as a pushed
             * full-screen thread — the alternative was losing their place mid-read. Migrating rather
             * than rendering it here also consumes the selection, so widening the window hours later
             * cannot resurrect a thread the user finished with.
             */
            val orphanedSelection = selectedPostId
            LaunchedEffect(orphanedSelection) {
                if (orphanedSelection != null) {
                    selectedPostId = null
                    onOpenPostFullScreen(orphanedSelection)
                }
            }
            PostListRoute(
                viewModel = listViewModel,
                onPostClick = onOpenPostFullScreen,
                onCreatePost = onCreatePost,
                onSignIn = onSignIn,
                onVerify = onVerify,
                onScrollActiveChanged = onFeedScrollActiveChanged,
            )
            return@BoxWithConstraints
        }

        // Back closes the thread rather than leaving the tab: the pane is where the user's attention is.
        BackHandler(enabled = selectedPostId != null) { selectedPostId = null }

        Row(Modifier.fillMaxSize()) {
            PostListRoute(
                viewModel = listViewModel,
                onPostClick = { postId -> selectedPostId = postId },
                onCreatePost = onCreatePost,
                onSignIn = onSignIn,
                onVerify = onVerify,
                modifier = Modifier.width(listPaneWidth),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) {
                val postId = selectedPostId
                if (postId == null) {
                    EmptyDetailPane()
                } else {
                    /*
                     * One disposable store per selection: switching threads clears the previous
                     * ViewModel instead of parking it in the entry's store forever. Entry-keyed
                     * ViewModels looked cheaper, but a long tablet session left every thread ever
                     * opened — full comment tree and all — alive until the entry itself died.
                     */
                    val storeOwner = remember(postId) { PaneViewModelStoreOwner() }
                    DisposableEffect(storeOwner) {
                        onDispose { storeOwner.viewModelStore.clear() }
                    }
                    val detailViewModel: PostDetailViewModel =
                        viewModel(
                            viewModelStoreOwner = storeOwner,
                            factory = PostDetailViewModel.factory(container, postId),
                        )
                    val replyViewModel: ReplyComposerViewModel =
                        viewModel(
                            viewModelStoreOwner = storeOwner,
                            factory = ReplyComposerViewModel.factory(container, postId),
                        )
                    PostDetailRoute(
                        viewModel = detailViewModel,
                        replyViewModel = replyViewModel,
                        onBack = { selectedPostId = null },
                        onOpenBrowser = onOpenBrowser,
                        onLinkClick = onLinkClick,
                        onAuthorClick = onAuthorClick,
                        onSignIn = onSignIn,
                        onVerify = onVerify,
                        onImageClick = onImageClick,
                    )
                }
            }
        }
    }
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

/** A ViewModel store the detail pane can discard wholesale when the selection moves on. */
private class PaneViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Buying an invite code is a site-side spend; the app only confirms it. */
private fun inviteWebKey(title: String): WebKey =
    WebKey(NodeSeekSite.BASE_URL + NodeSeekSite.INVITE_PATH, title, WebViewGoal.MANAGE)

private fun imageViewerKeyFor(urls: List<String>, url: String): ImageViewerKey =
    ImageViewerKey(urls = urls, index = urls.indexOf(url).coerceAtLeast(0))

private val TWO_PANE_MIN_WIDTH = 600.dp
private val WIDE_WINDOW_WIDTH = 840.dp
private val LIST_PANE_WIDTH = 260.dp
private val WIDE_LIST_PANE_WIDTH = 320.dp
