package io.github.nodyssey.ui.common

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * The controller a modal should be presented from: the topmost one on the foreground scene's key
 * window.
 *
 * Found rather than injected. Compose Multiplatform's iOS entry point is itself a `UIViewController`,
 * so a composable could in principle be handed one — but every seam that needs this is an `expect`
 * whose Android side takes nothing, and threading a controller down through `commonMain` to reach
 * four call sites is a worse trade than one walk up the presentation chain.
 *
 * The walk past `presentedViewController` matters: presenting from the root while a sheet is already
 * up puts the new one behind it, and iOS logs a warning rather than showing anything.
 */
internal fun topmostViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene }
    val window =
        (scene as? UIWindowScene)?.windows?.firstOrNull { window ->
            (window as? UIWindow)?.isKeyWindow() == true
        } as? UIWindow
    var controller = window?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
