package io.github.nodyssey.ios

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.nodyssey.NodysseyRoot
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.plaza.core.net.resolveWebKitUserAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * What the Xcode target calls, and the whole of what Swift has to know about this app.
 *
 * `MainActivity` and `NodysseyApp` are the Android counterparts and both are synchronous; this one
 * takes a callback, and the reason is [resolveWebKitUserAgent]. The `User-Agent` this app sends has
 * to be the one WebKit sends, or a `cf_clearance` earned in the sign-in browser is rejected on the
 * very next API call — and WebKit will only say what its user agent is by running a line of
 * JavaScript, which is asynchronous. So the graph is built after that answer arrives rather than
 * around a guess, and the launch screen stays up for the milliseconds it takes.
 *
 * **Built once per process, not once per call**, and both halves of that matter:
 *
 * - The container owns a Room database and six DataStore files. A second instance over the same
 *   files is not a duplicate, it is a corruption: DataStore raises `IllegalStateException` the moment
 *   two of them serve one file in one process.
 * - The controller holds the composition, and the composition holds the navigation back stack.
 *   Rebuilding it puts the reader back on the feed.
 *
 * Neither is hypothetical. A scene is disconnected and reconnected by the system — backgrounding
 * this app and returning to it does exactly that — and `scene(_:willConnectTo:)` calls this each
 * time. The first version of this shell built a fresh graph on every one of them, and the way that
 * showed was a post detail screen that had become the feed while the app was in the background.
 *
 * `UIApplicationSupportsMultipleScenes` is false, which is what makes one controller the right
 * number: reusing a view controller across two live scenes would not be.
 *
 * @param onReady called on the main thread with the controller to install as the window's root.
 */
object NodysseyApp {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var root: UIViewController? = null
    private var building: Deferred<UIViewController>? = null

    fun start(onReady: (UIViewController) -> Unit) {
        root?.let {
            onReady(it)
            return
        }
        // The build, not a flag saying one is under way. Re-checking `root` inside the coroutine was
        // not enough and could not be: [resolveWebKitUserAgent] suspends, and two `willConnectTo`
        // calls before the first answer arrives both get past *any* check made before that point.
        // The second one would then build a second container over the same Room database and the same
        // six DataStore files — the corruption this object's KDoc is about — and hand the window a
        // second controller with an empty back stack. Awaiting one `Deferred` is what makes the
        // second caller a listener rather than a builder.
        //
        // No lock, and none needed: `start` is called from `scene(_:willConnectTo:)`, which is the
        // main thread, and this scope dispatches to the main thread — so the read and the write below
        // cannot interleave with another call's.
        val pending = building ?: scope.async { build() }.also { building = it }
        scope.launch { onReady(pending.await()) }
    }

    private suspend fun build(): UIViewController {
        val graph = IosAppContainer(userAgent = resolveWebKitUserAgent(NodeSeekSite.CONFIG))

        // Set before the first composition asks for an image, and once: `setSafe` is a no-op if
        // something already installed a loader, which is what makes a second call harmless.
        SingletonImageLoader.setSafe { context -> imageLoader(context, graph) }

        val controller =
            ComposeUIViewController {
                NodysseyRoot(
                    container = graph,
                    // No notification extra and no deep link to read yet: both arrive through
                    // `UIApplicationDelegate`, and neither has an iOS half — the poll worker is
                    // step D4 and Universal Links have no association file. See
                    // `AppLinkHandling.ios.kt`.
                    initialTab = TopLevelDestination.HOME,
                    launchRequest = null,
                    onLaunchRequestHandled = {},
                )
            }
        root = controller
        // Nothing left to await; the fast path above answers from here on.
        building = null
        return controller
    }

    private fun imageLoader(context: PlatformContext, container: IosAppContainer): ImageLoader =
        nodysseyImageLoader(context) { container.urlSession }
}
