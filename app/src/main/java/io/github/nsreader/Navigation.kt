package io.github.nsreader

import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import io.github.nsreader.ui.composer.PostComposerRoute
import io.github.nsreader.ui.composer.PostComposerViewModel
import io.github.nsreader.ui.login.WebViewGoal
import io.github.nsreader.ui.login.WebViewRoute
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
        if (atTabRoot) {
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
                    PostListRoute(
                        viewModel = viewModel,
                        onPostClick = { backStack.add(PostDetailKey(it)) },
                        onCreatePost = { backStack.add(PostComposerKey) },
                        onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                        onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                    )
                }

                entry<SearchKey> {
                    val viewModel: SearchViewModel =
                        viewModel(factory = SearchViewModel.factory(container))
                    SearchRoute(
                        viewModel = viewModel,
                        onPostClick = { backStack.add(PostDetailKey(it)) },
                        onUserClick = { uid -> openExternalUrl(NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(uid)) },
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
                        onEditProfile = { backStack.add(AccountProfileFieldsKey) },
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

                entry<PostDetailKey> { key ->
                    // Keyed so navigating to a different post builds a fresh ViewModel.
                    val viewModel: PostDetailViewModel =
                        viewModel(
                            key = "post-${key.postId}",
                            factory = PostDetailViewModel.factory(container, key.postId),
                        )
                    PostDetailRoute(
                        viewModel = viewModel,
                        initialFloor = key.floor,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenBrowser = openExternalUrl,
                        onSignIn = { backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN)) },
                        onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
                        onImageClick = openExternalUrl,
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
