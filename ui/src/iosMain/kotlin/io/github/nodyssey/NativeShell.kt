package io.github.nodyssey

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.assets.PendingAttendance
import io.github.nodyssey.ui.common.LocalOpenNetworkCheck
import io.github.nodyssey.ui.compare.LocalOpenReportCompare
import io.github.nodyssey.ui.composer.PendingComposerShare
import io.github.nodyssey.ui.navigation.StackMirror
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.notifications.NotificationsViewModel
import io.github.nodyssey.ui.onboarding.OnboardingScreen
import io.github.nodyssey.ui.postlist.HomeFeedStates
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.about_privacy
import io.github.nodyssey.ui.resources.about_rss
import io.github.nodyssey.ui.resources.about_site
import io.github.nodyssey.ui.resources.app_name
import io.github.nodyssey.ui.settings.rememberAppLinkHandlingEnabled
import io.github.nodyssey.ui.settings.rememberAppLinkSettingsLauncher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.hidesBottomBarWhenPushed
import platform.UIKit.modalInPresentation
import platform.UIKit.tabBarItem
import platform.UIKit.transitionCoordinator
import platform.darwin.NSObject

/**
 * The iOS app: a system tab bar and a system navigation stack per tab, with one Compose screen in
 * each pushed controller.
 *
 * What this buys over the single `ComposeUIViewController` the app used to be is everything iOS
 * draws for a native app and Compose can only imitate — the Liquid Glass tab bar, the push and pop
 * transitions, the edge swipe and iOS 26's swipe-anywhere back, all of it behaving the way every
 * other app on the phone does.
 *
 * **The back stacks are still the truth.** Every screen navigates the way it does on Android, with
 * `backStack.add` and `removeLastOrNull` on the [NavBackStack] its entry was built against — no entry
 * file knows this shell exists. A [StackMirror] per tab turns each change into the controllers its
 * `UINavigationController` should hold, and the one move UIKit makes on its own, popping, is written
 * back from the navigation delegate.
 *
 * What the Android root has and this does not:
 * - **Exit through 首页.** A system tab bar does not pop into another tab, and nobody on iOS expects
 *   it to.
 * - **List-detail on a wide window.** An iPad draws one screen at a time here for now.
 * - **The shared-element flight from a feed row into a thread.** It needs both ends in one
 *   composition, and each screen here is its own. `LocalThreadTransition` is simply never provided,
 *   which the screens already handle — it is what a two-pane window and 墨水屏模式 look like too.
 * - **Hide-on-scroll for the tab bar.** The bar is glass over the content rather than a slab under
 *   it, which is the reason to hide one.
 */
suspend fun nativeShellViewController(container: AppContainer): UIViewController {
    // Read before anything is drawn, for the reason `NodysseyRoot` takes `initialSettings`: the first
    // frame of every screen would otherwise go out in the factory colours.
    val settings = container.settingsRepository.settings.first()
    // Resolved once rather than per composition. Entry lambdas are not composable, and on this
    // platform a change of 语言 takes effect at the next launch anyway — see `AppLanguageControl`.
    val strings =
        ShellStrings(
            siteTitle = container.appVersion.label.ifBlank { getString(Res.string.app_name) },
            aboutSiteTitle = getString(Res.string.about_site),
            privacyTitle = getString(Res.string.about_privacy),
            rssLabel = getString(Res.string.about_rss),
            tabTitles = TopLevelDestination.entries.associateWith { getString(it.label) },
        )
    return NativeShell(container, settings, strings).root
}

private class ShellStrings(
    val siteTitle: String,
    val aboutSiteTitle: String,
    val privacyTitle: String,
    val rssLabel: String,
    val tabTitles: Map<TopLevelDestination, String>,
)

private class NativeShell(
    private val container: AppContainer,
    initialSettings: UserSettings,
    strings: ShellStrings,
) {
    private val scope = MainScope()

    /** The newest settings, so a screen pushed later starts in today's colours rather than launch's. */
    private var settings: UserSettings = initialSettings

    /**
     * What `MainNavigation` keeps in its own composition, kept here because no composition here
     * outlives the screens: the notifications view model behind both the tab badge and the 通知
     * screen, and 首页's list positions.
     */
    private val shellStore = ViewModelStore()
    private val notificationsViewModel: NotificationsViewModel =
        ViewModelProvider.create(shellStore, NotificationsViewModel.factory(container))[NotificationsViewModel::class]
    private val homeFeedStates = HomeFeedStates()
    private val pendingComposerShare = PendingComposerShare()
    private val pendingAttendance = PendingAttendance()
    private var homeReselectRequests by mutableIntStateOf(0)
    private var notificationsScrollToTopRequests by mutableIntStateOf(0)

    private val stacks: Map<TopLevelDestination, NavBackStack<NavKey>> =
        TopLevelDestination.entries.associateWith { NavBackStack(it.key) }

    val root = UITabBarController()

    private val navigationControllers: Map<TopLevelDestination, UINavigationController> =
        TopLevelDestination.entries.associateWith { destination ->
            UINavigationController().apply {
                // Every screen still draws its own app bar, back arrow included; the system one
                // would be a second bar above it.
                setNavigationBarHidden(true, animated = false)
                tabBarItem =
                    UITabBarItem(
                        title = strings.tabTitles.getValue(destination),
                        image = UIImage.systemImageNamed(destination.symbol),
                        selectedImage = UIImage.systemImageNamed("${destination.symbol}.fill"),
                    )
            }
        }

    private val dependencies =
        NavigationDependencies(
            container = container,
            siteTitle = strings.siteTitle,
            aboutSiteTitle = strings.aboutSiteTitle,
            privacyTitle = strings.privacyTitle,
            rssLabel = strings.rssLabel,
            uriHandler = SafariUriHandler,
            // The same gate `MainNavigation` puts in front of the browser.
            openExternalUrl = { url ->
                if (NodeSeekSite.isExternalWebUrl(url) || NodeSeekSite.isMailUrl(url)) {
                    runCatching { SafariUriHandler.openUri(url) }
                }
            },
            notificationsViewModel = notificationsViewModel,
            homeFeedStates = homeFeedStates,
            homeReselectRequests = { homeReselectRequests },
            notificationsScrollToTopRequests = { notificationsScrollToTopRequests },
            isListDetailExpanded = { false },
            isEinkMode = { settings.einkMode },
            onTabBarHiddenByScroll = {},
            selectTab = { root.selectedIndex = it.ordinal.toULong() },
            scope = scope,
            pendingComposerShare = pendingComposerShare,
            pendingAttendance = pendingAttendance,
        )

    private val providers: Map<TopLevelDestination, (NavKey) -> NavEntry<NavKey>> =
        TopLevelDestination.entries.associateWith { destinationProvider(stacks.getValue(it), dependencies) }

    /** Controllers popped but possibly still animating out; see [flushReleases]. */
    private val pendingRelease = mutableListOf<Page>()

    private val mirrors: Map<TopLevelDestination, StackMirror<NavKey, Page>> =
        TopLevelDestination.entries.associateWith { destination ->
            StackMirror(
                create = { index, key -> page(destination, index, key) },
                release = { pendingRelease += it },
            )
        }

    /** Tabs whose stack changed while their controller was mid-transition. */
    private val deferredSyncs = mutableSetOf<TopLevelDestination>()

    // UIKit holds delegates weakly; these fields are what keeps them alive.
    private val tabBarDelegate = TabBarDelegate()
    private val navigationDelegates = TopLevelDestination.entries.associateWith { NavigationDelegate(it) }
    private val popGestureDelegates = TopLevelDestination.entries.associateWith { PopGestureDelegate(it) }

    init {
        root.delegate = tabBarDelegate
        root.setViewControllers(TopLevelDestination.entries.map { navigationControllers.getValue(it) }, animated = false)
        TopLevelDestination.entries.forEach { destination ->
            val navigationController = navigationControllers.getValue(destination)
            navigationController.delegate = navigationDelegates.getValue(destination)
            // With the bar hidden UIKit switches both swipes off — the edge one and iOS 26's
            // swipe-anywhere one — on the theory that a screen without a back button has nothing to go
            // back to. These screens draw their own. The swipe-anywhere recognizer is iOS 26 API on a
            // target that starts at 16, hence the check; Compose Multiplatform already knows to stop
            // it when a drag belongs to something scrolling sideways inside the page.
            //
            // The view first: UIKit hands both recognizers back to its own delegate when it loads a
            // navigation controller's view, and a tab's view loads the first time the tab is shown.
            // Set before that, the delegate held for 首页 only — every screen under 通知 and 我的
            // went back by its arrow alone.
            navigationController.loadViewIfNeeded()
            val popDelegate = popGestureDelegates.getValue(destination)
            navigationController.interactivePopGestureRecognizer?.delegate = popDelegate
            if (navigationController.hasContentPopGesture()) {
                navigationController.interactiveContentPopGestureRecognizer?.delegate = popDelegate
            }
            sync(destination)
            scope.launch {
                snapshotFlow { stacks.getValue(destination).toList() }.collect { sync(destination) }
            }
        }
        observeSettings()
        observeNotifications()
    }

    /** One screen: a Compose controller showing [key]'s entry, and the view models it owns. */
    private class Page(val controller: UIViewController, val owner: PageOwner)

    private class PageOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun page(destination: TopLevelDestination, index: Int, key: NavKey): Page {
        val stack = stacks.getValue(destination)
        val entry = providers.getValue(destination)(key)
        val owner = PageOwner()
        val controller =
            ComposeUIViewController {
                NodysseyChrome(container, settings) {
                    CompositionLocalProvider(
                        // The page's own store rather than the controller's default one, so that a
                        // view model lives exactly as long as its entry is on the stack — see
                        // [flushReleases] — and not as long as UIKit keeps the controller around.
                        LocalViewModelStoreOwner provides owner,
                        // 网络自检 from any screen's network-error state — see [LocalOpenNetworkCheck].
                        LocalOpenNetworkCheck provides { stack.add(NetworkCheckKey) },
                        // 测评对比 from any report card's 对比 (n), onto this page's tab.
                        LocalOpenReportCompare provides { stack.add(ReportCompareKey) },
                    ) {
                        entry.Content()
                    }
                }
            }
        // The tab bar belongs to the tab roots, as on Android: a thread or a settings page pushed
        // over 首页 is full-screen.
        controller.hidesBottomBarWhenPushed = index > 0
        return Page(controller, owner)
    }

    private fun sync(destination: TopLevelDestination) {
        val navigationController = navigationControllers.getValue(destination)
        // `setViewControllers` during a push or a swipe is how UIKit ends up with a bar for one
        // screen over the content of another. The navigation delegate runs this again once the
        // transition has settled.
        if (navigationController.transitionCoordinator != null) {
            deferredSyncs += destination
            return
        }
        val pages = mirrors.getValue(destination).reconcile(stacks.getValue(destination).toList()) ?: return
        val onScreen = navigationController.view.window != null
        navigationController.setViewControllers(pages.map { it.controller }, animated = onScreen)
        if (!onScreen) flushReleases()
    }

    /**
     * Clears the view models of every page that has left its stack.
     *
     * Not at the moment it leaves: a popped page is still on screen for the length of the pop, and a
     * view model cleared under it cancels whatever it is showing. The navigation delegate calls this
     * when the transition is over, and [sync] calls it at once for a tab that is not on screen.
     */
    private fun flushReleases() {
        pendingRelease.forEach { it.owner.viewModelStore.clear() }
        pendingRelease.clear()
    }

    private fun observeSettings() {
        scope.launch {
            container.settingsRepository.settings.collect { latest ->
                settings = latest
                // The bars UIKit draws follow the app's own light/dark answer, not only the OS's, the
                // way `NodysseyRoot` makes the status bar follow it on Android.
                root.overrideUserInterfaceStyle =
                    when {
                        latest.einkMode -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                        latest.themeMode == ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                        latest.themeMode == ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
                        else -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
                    }
            }
        }
        scope.launch {
            container.settingsRepository.settings
                .map { it.onboardingSeen }
                .distinctUntilChanged()
                .collect { seen -> if (seen) dismissOnboarding() else presentOnboarding() }
        }
    }

    private var onboarding: UIViewController? = null

    /**
     * 新手引导, presented over the tabs rather than drawn over them.
     *
     * Watched rather than shown once at launch for the reason the Android root overlays it instead of
     * swapping it in: 再看一次引导 on 使用帮助 clears the flag from deep in a stack, and the guide has
     * to come back over that stack without taking it away.
     */
    private suspend fun presentOnboarding() {
        if (onboarding != null) return
        // Presenting needs a controller already in a window, and the shell is installed as the
        // window's root only after this object has been built and handed back.
        while (root.view.window == null) delay(16)
        val controller =
            ComposeUIViewController {
                NodysseyChrome(container, settings) {
                    OnboardingScreen(
                        onFinish = { scope.launch { container.settingsRepository.setOnboardingSeen(true) } },
                        appLinksEnabled = rememberAppLinkHandlingEnabled(),
                        onOpenAppLinkSettings = rememberAppLinkSettingsLauncher(),
                    )
                }
            }
        controller.modalPresentationStyle = UIModalPresentationFullScreen
        controller.modalInPresentation = true
        onboarding = controller
        root.presentViewController(controller, animated = false, completion = null)
    }

    private fun dismissOnboarding() {
        val controller = onboarding ?: return
        onboarding = null
        controller.dismissViewControllerAnimated(true, completion = null)
    }

    private fun observeNotifications() {
        scope.launch {
            notificationsViewModel.uiState
                .map { it.counts.all }
                .distinctUntilChanged()
                .collect { unread ->
                    navigationControllers.getValue(TopLevelDestination.NOTIFICATIONS).tabBarItem.badgeValue =
                        unread.takeIf { it > 0 }?.toString()
                }
        }
        // The Android root refreshes the badge on every resume, because a count grown stale overnight
        // is most visibly wrong the moment the app comes back. This is that resume.
        notificationsViewModel.refreshIfStale()
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> notificationsViewModel.refreshIfStale() }
    }

    private fun destinationOf(controller: UIViewController?): TopLevelDestination? =
        navigationControllers.entries.firstOrNull { it.value == controller }?.key

    private inner class TabBarDelegate :
        NSObject(),
        UITabBarControllerDelegateProtocol {
        /*
         * Tapping the tab that is already showing. Deeper in its stack, UIKit pops to the root by
         * itself and the navigation delegate writes that back. At the root, it is the same request as
         * on Android: 首页 back to the top and reloaded, 通知 back to the top, 我的 nothing.
         */
        override fun tabBarController(
            tabBarController: UITabBarController,
            shouldSelectViewController: UIViewController,
        ): Boolean {
            val destination = destinationOf(shouldSelectViewController) ?: return true
            if (shouldSelectViewController == tabBarController.selectedViewController &&
                stacks.getValue(destination).size == 1
            ) {
                when (destination) {
                    TopLevelDestination.HOME -> homeReselectRequests++
                    TopLevelDestination.NOTIFICATIONS -> notificationsScrollToTopRequests++
                    TopLevelDestination.PROFILE -> Unit
                }
            }
            return true
        }
    }

    private inner class NavigationDelegate(
        private val destination: TopLevelDestination,
    ) : NSObject(),
        UINavigationControllerDelegateProtocol {
        /*
         * Called when a transition has finished, whichever side started it. After a pop UIKit made —
         * a swipe, the tab bar's pop-to-root — the controller holds fewer screens than the mirror,
         * and the difference comes off the back stack. After a change this shell made the counts
         * already agree and nothing is written. A swipe that was let go of halfway lands here too,
         * with nothing popped.
         */
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean,
        ) {
            val dropped = mirrors.getValue(destination).platformPopped(navigationController.viewControllers.size)
            val stack = stacks.getValue(destination)
            repeat(dropped) { stack.removeLastOrNull() }
            flushReleases()
            if (deferredSyncs.remove(destination)) sync(destination)
        }
    }

    private inner class PopGestureDelegate(
        private val destination: TopLevelDestination,
    ) : NSObject(),
        UIGestureRecognizerDelegateProtocol {
        override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean =
            navigationControllers.getValue(destination).viewControllers.size > 1
    }
}

/** Whether this system has iOS 26's swipe-anywhere back, which a target starting at 16 cannot assume. */
@OptIn(ExperimentalForeignApi::class)
private fun UINavigationController.hasContentPopGesture(): Boolean =
    respondsToSelector(NSSelectorFromString("interactiveContentPopGestureRecognizer"))

/** The SF Symbol for a tab; its selected state is the `.fill` variant, as Material's is the filled icon. */
private val TopLevelDestination.symbol: String
    get() =
        when (this) {
            TopLevelDestination.HOME -> "house"
            TopLevelDestination.NOTIFICATIONS -> "bell"
            TopLevelDestination.PROFILE -> "person"
        }

/**
 * Safari, which is what the platform `UriHandler` is on this side — the same one `BrowserLinks.ios`
 * hands back inside a composition, needed here outside of one.
 */
private object SafariUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val url = NSURL.URLWithString(uri) ?: throw IllegalArgumentException("Not a URL: $uri")
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
