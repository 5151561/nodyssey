@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import androidx.compose.runtime.mutableStateOf
import io.github.nodyssey.ui.navigation.NativeTabBar
import io.github.nodyssey.ui.navigation.TopLevelDestination
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIImage
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.tabBarItem
import platform.darwin.NSObject

/**
 * The bar the app borrows from `UITabBarController`, and the composition it floats over.
 *
 * iOS 26's Liquid Glass tab bar is drawn by the system, and only inside a `UITabBarController`: a
 * `UITabBar` on its own draws the new shape and then paints an opaque background over it, and a bar
 * Compose draws gets no material at all. Both were measured, and the difference is not subtle — the
 * bare bar is a flat white slab over the content, the controller's is translucent enough to take its
 * colour from what is behind it.
 *
 * What this does **not** do is give the controller a child per tab. It is given three empty children
 * purely so its bar has three items, and the composition is hosted as a sibling underneath instead.
 * The alternative — one `ComposeUIViewController` per tab — is what the platform expects, and it
 * costs a restructuring that this app does not need: the badge, the onboarding overlay and the update
 * reminder all live above the tabs and would each have to be hoisted out of a composition that no
 * longer exists as one.
 *
 * Three things here are load-bearing and each fails in a way that does not name itself:
 *
 * 1. **The bar's delegate belongs to the controller.** `-[UITabBar setDelegate:]` raises rather than
 *    yielding, so the first version of this died in `buildController` with an `NSInvalidArgumentException`
 *    and nothing on screen. Selection arrives through `UITabBarControllerDelegate` instead.
 * 2. **`tabBar` is not a direct subview.** On iOS 26 `view.subviews.indexOf(tabBar)` is `-1` — it is
 *    wrapped in a container — so `insertSubview(belowSubview = tabBar)` has no target and silently
 *    places the content over the bar. Inserting at index 0 is what actually puts it underneath.
 * 3. **The selected child's container eats touches.** It covers everything above the bar and answers
 *    none of them, and a screen where nothing scrolls looks exactly like a frozen app. It is turned
 *    off on every layout pass, because setting it once does not hold.
 */
internal class NodysseyTabBarHost(
    makeCompose: (NativeTabBar) -> UIViewController,
) : UITabBarController(nibName = null, bundle = null) {
    /**
     * Built by the init block rather than handed in, because the composition needs [bridge] and
     * [bridge] needs this object — a cycle the factory parameter breaks.
     */
    private val composed: UIViewController

    private val shownTab = mutableStateOf(TopLevelDestination.HOME)
    private val barHeight = mutableStateOf(0f)
    private val selectionDelegate = SelectionDelegate { index -> shownTab.value = BAR_TABS[index].destination }

    val bridge =
        NativeTabBar(
            selected = shownTab,
            onTabChanged = { destination ->
                selectedIndex = BAR_TABS.indexOfFirst {
                    it.destination == destination
                }.coerceAtLeast(0).toULong()
            },
            onTabRootVisible = { visible -> tabBar.hidden = !visible },
            onBadgeChanged = { count ->
                // `nil` rather than "0": an empty string still draws a badge, a zero one.
                tabBar.items?.getOrNull(NOTIFICATIONS_TAB_INDEX)
                    ?.let { (it as UITabBarItem).badgeValue = if (count > 0) count.toString() else null }
            },
            height = barHeight,
        )

    init {
        setViewControllers(
            BAR_TABS.map { tab ->
                UIViewController().apply {
                    tabBarItem =
                        UITabBarItem(
                            title = tab.label,
                            image = UIImage.systemImageNamed(tab.symbol),
                            tag = 0,
                        )
                }
            },
            animated = false,
        )
        // Held as a property as well, because `UITabBarController.delegate` is weak: handed over and
        // forgotten, it would be collected and the bar would go quiet.
        this.delegate = selectionDelegate

        // Loaded now so they can be neutralised before they are ever shown. A child whose view was
        // never loaded cannot cover anything; one that loads later can.
        viewControllers?.forEach { child ->
            (child as UIViewController).view.setUserInteractionEnabled(false)
        }

        composed = makeCompose(bridge)
        addChildViewController(composed)
        view.insertSubview(composed.view, atIndex = 0L)
        composed.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        composed.view.setFrame(view.bounds)
        barHeight.value = tabBar.frame.useContents { size.height }.toFloat()

        // Re-applied on every pass, and it has to be: the container re-enables itself, so setting it
        // once does not hold. Everything above the composition is turned off except the bar's own
        // ancestors — the one that must go is the `UITransitionView` between them, which covers the
        // whole content area and answers none of the touches it swallows. A screen where nothing
        // scrolls looks exactly like a frozen app, and it is the failure this loop exists to stop.
        //
        // The composition is spared explicitly. Leaving that out is a fix that reads as having done
        // nothing: the bar is not inside the composition, so the same test that protects the bar
        // condemns the content.
        view.subviews.filterIsInstance<UIView>().forEach { subview ->
            if (subview !== composed.view && !tabBar.isDescendantOfView(subview)) {
                subview.setUserInteractionEnabled(false)
            }
        }
    }

    private class SelectionDelegate(
        private val onSelect: (Int) -> Unit,
    ) : NSObject(),
        UITabBarControllerDelegateProtocol {
        override fun tabBarController(
            tabBarController: UITabBarController,
            didSelectViewController: UIViewController,
        ) {
            onSelect(tabBarController.selectedIndex.toInt())
        }
    }
}

/**
 * Whether the system's bar is available at all.
 *
 * iOS 26 is where Liquid Glass arrived, and the deployment target is 16 — so this is a fork, not a
 * preference. The older path is the app as it was: one composition, and a bar Compose draws itself.
 */
internal fun systemTabBarAvailable(): Boolean =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }

private data class BarTab(val label: String, val symbol: String, val destination: TopLevelDestination)

/** The unread badge's item, by position in [BAR_TABS]. */
private const val NOTIFICATIONS_TAB_INDEX = 1

/**
 * The order is the bar's, which `TopLevelDestination`'s own order happens to match today — and that
 * is the reason it is written out rather than derived. A fourth destination, or a reordering of the
 * enum, would silently move an item on the bar or put the badge on the wrong one.
 */
private val BAR_TABS =
    listOf(
        BarTab("首页", "house", TopLevelDestination.HOME),
        BarTab("通知", "bell", TopLevelDestination.NOTIFICATIONS),
        BarTab("我的", "person", TopLevelDestination.PROFILE),
    )
