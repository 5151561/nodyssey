@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * `UIActivityViewController`, which is what 分享 means on iOS.
 *
 * [chooserTitle] is dropped: the share sheet is titled by the system and labelling it is not
 * something the API offers. It stays in the signature because Android's chooser does take one.
 *
 * The presenting controller is found rather than injected — the topmost one on the foreground scene's
 * key window, walking past anything already presented so the sheet does not try to come up behind a
 * dialog. That walk is the part with no test: nothing has launched this yet, because iOS has no shell
 * to launch it from. It is written out rather than left a no-op so that the day a shell exists this is
 * a thing to run, not a thing to notice missing.
 */
@Composable
actual fun rememberShareText(): (text: String, chooserTitle: String?) -> Unit =
    remember {
        { text, _ ->
            topmostViewController()?.let { host ->
                val sheet =
                    UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
                // The iPad presentation is a popover and needs an anchor; the window itself is the
                // only one available from here, which puts the arrow at its centre.
                sheet.popoverPresentationController?.sourceView = host.view
                host.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }

private fun topmostViewController(): platform.UIKit.UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene }
    val window = (scene as? UIWindowScene)?.windows?.firstOrNull { window ->
        (window as? platform.UIKit.UIWindow)?.isKeyWindow() == true
    } as? platform.UIKit.UIWindow
    var controller = window?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
