@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import androidx.compose.runtime.mutableStateOf
import io.github.nodyssey.ui.navigation.NativeTabBar
import io.github.nodyssey.ui.navigation.TopLevelDestination
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIImage
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.darwin.NSObject

/**
 * The system's tab bar, laid over the composition on iOS 26 and later.
 *
 * iOS 26's Liquid Glass tab bar is drawn by the system, and a bar drawn by Compose gets no material
 * at all — which is why this exists rather than a Material bar with a blur.
 *
 * **What it deliberately is not.** An earlier version borrowed a `UITabBarController`, giving it
 * three empty children purely so its bar had three items and hosting the composition as a sibling.
 * That worked, and brought three things with it: a `UITransitionView` between the bar and the
 * content that swallowed every touch on the screen, a `tabBar` that is not a direct subview and so
 * could not be inserted beneath, and a delegate slot the controller already owned, where assigning
 * one aborts. None of it was needed. A bare `UITabBar` renders the material perfectly well — it
 * simply has to be sized to the bar *plus* the bottom safe area and reach the display edge, so that
 * its background has something to fill. Given only its intrinsic height, above the home indicator,
 * it falls back to an opaque one, and that single mis-sizing is what sent this file down the
 * controller path in the first place.
 *
 * **Why the bar is a plain subview rather than a `UIKitView`.** It was a `UIKitView`, and on device
 * a strip of white appeared beneath it that the simulator would not reproduce. Compose's interop
 * wraps whatever view it is handed in a container of its own, and what that container paints is not
 * something this side can see or set. Two sibling views in one plain `UIViewController` leave no
 * such container between them — which is the arrangement below, and the same one a shipping app
 * arrived at with a SwiftUI `ZStack`.
 *
 * The composition runs full-bleed underneath on purpose. Glass refracts what is behind it, and a bar
 * over an empty strip has nothing to sample.
 *
 * (`addChildViewController` and `didMoveToParentViewController` below arrive through an import of
 * their own. UIKit declares them on a category, so Kotlin/Native makes them extensions — and a
 * missing one reads as `Unresolved reference` rather than as anything about categories.)
 */
internal class NodysseyTabBarHost {
    private val shownTab = mutableStateOf(TopLevelDestination.HOME)
    private val barHeight = mutableStateOf(0f)
    private val delegate = SelectionDelegate { index -> shownTab.value = BAR_TABS[index].destination }

    /**
     * Kept beside the bar rather than read back off it: `UITabBar.items` is `List<*>` in the
     * bindings, so every read would need a cast, and the badge and the selection both need one.
     */
    private val items =
        BAR_TABS.map { tab ->
            UITabBarItem(title = tab.label, image = UIImage.systemImageNamed(tab.symbol), tag = 0)
        }

    private val bar = UITabBar()

    private lateinit var contentView: UIView

    private val controller = Container()

    val bridge =
        NativeTabBar(
            selected = shownTab,
            onTabChanged = { destination -> bar.selectedItem = items.getOrNull(indexOf(destination)) },
            onTabRootVisible = { visible -> bar.hidden = !visible },
            onBadgeChanged = { count ->
                // `nil` rather than "0": an empty string still draws a badge, a zero one.
                items[NOTIFICATIONS_TAB_INDEX].badgeValue = if (count > 0) count.toString() else null
            },
            height = barHeight,
        )

    /**
     * Puts the composition and the bar in one plain controller and hands it back as the window's
     * root. A method rather than a constructor because the composition needs [bridge] and the
     * host needs the composition — a cycle the ordering breaks: build the host, build the
     * composition around its bridge, then attach.
     */
    fun attach(content: UIViewController): UIViewController {
        /*
         * Wired in an initialiser rather than an `apply` block, and not for style: inside an `apply`,
         * `items` and `delegate` both resolve to the bar's own Objective-C properties instead of the
         * fields above. That compiles and reads correctly while quietly taking the item list off the
         * wrong object.
         *
         * The delegate is held by this class as well as by the bar because `UITabBar.delegate` is
         * weak: handed over and forgotten, it is collected and the bar goes quiet.
         */
        bar.setItems(items, animated = false)
        bar.delegate = delegate
        bar.selectedItem = items.first()

        contentView = content.view
        controller.addChildViewController(content)
        // Subview order is the whole layering: the composition at the bottom, the bar over it, and
        // nothing in between.
        controller.view.addSubview(contentView)
        controller.view.addSubview(bar)
        content.didMoveToParentViewController(controller)
        controller.onLayout = ::layout
        return controller
    }

    private fun layout() {
        val bounds = controller.view.bounds
        contentView.setFrame(bounds)

        val total = BAR_HEIGHT + controller.view.safeAreaInsets.useContents { bottom }
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        bar.setFrame(CGRectMake(0.0, height - total, width, total))

        // What the rest of the app pads by: arithmetic on two known values rather than a measurement
        // off the view. `tabBar.frame` is not the visible bar once the system floats it, and reading
        // it was wrong once already.
        barHeight.value = total.toFloat()
    }

    /**
     * The one thing the container exists for: a layout pass to keep the two frames in step. A plain
     * `UIViewController`, because a `UITabBarController` would put its own content view between the
     * bar and the composition — and that view is what ate the touches.
     */
    private class Container : UIViewController(nibName = null, bundle = null) {
        lateinit var onLayout: () -> Unit

        override fun viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            onLayout()
        }
    }

    private class SelectionDelegate(
        private val onSelect: (Int) -> Unit,
    ) : NSObject(),
        UITabBarDelegateProtocol {
        override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
            onSelect(BAR_TABS.indexOfFirst { it.label == didSelectItem.title })
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

/** The classic bar's height, which Apple does not publish a metric for on the floating one. */
private const val BAR_HEIGHT = 49.0

private data class BarTab(val label: String, val symbol: String, val destination: TopLevelDestination)

private const val NOTIFICATIONS_TAB_INDEX = 1

private fun indexOf(destination: TopLevelDestination): Int =
    BAR_TABS.indexOfFirst { it.destination == destination }

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
