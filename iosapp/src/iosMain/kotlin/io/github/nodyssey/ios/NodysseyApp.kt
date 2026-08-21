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

    /**
     * The graph, and the one thing every path here shares. Split from [root] because a background
     * task can need it without any window: `BGTaskScheduler` may relaunch this app straight into the
     * background, where no scene connects and so [start] is never called, yet the drain still has to
     * find a container. [ensureContainer] builds it for whichever gets here first.
     */
    private var container: IosAppContainer? = null
    private var containerBuild: Deferred<IosAppContainer>? = null

    private var root: UIViewController? = null
    private var controllerBuild: Deferred<UIViewController>? = null

    /**
     * Owned here, not in the container, because [register] has to run before the graph exists —
     * `BGTaskScheduler` launch handlers must be installed before the app finishes launching. Its
     * resolver closes back over [ensureContainer], so the one instance both queues work (through the
     * container) and drains it (through its handlers).
     */
    private val offlineScheduler = BgTaskOfflineScheduler(scope) { ensureContainer() }

    /**
     * Called from `AppDelegate.application(_:didFinishLaunchingWithOptions:)`, and it must be: iOS
     * traps if a `BGTaskScheduler` handler is registered after launch completes.
     */
    fun registerBackgroundTasks() = offlineScheduler.register()

    fun start(onReady: (UIViewController) -> Unit) {
        root?.let {
            onReady(it)
            return
        }
        // The build, not a flag saying one is under way. Re-checking `root` inside the coroutine was
        // not enough and could not be: [ensureContainer] suspends on WebKit, and two `willConnectTo`
        // calls before the first answer arrives both get past *any* check made before that point.
        // Awaiting one `Deferred` is what makes the second caller a listener rather than a builder.
        //
        // No lock, and none needed: `start` is called from `scene(_:willConnectTo:)`, which is the
        // main thread, and this scope dispatches to the main thread — so the read and the write below
        // cannot interleave with another call's. The same holds for [ensureContainer]'s own guard.
        val pending = controllerBuild ?: scope.async { buildController() }.also { controllerBuild = it }
        scope.launch { onReady(pending.await()) }
    }

    /**
     * The graph, built once per process and shared by the UI and the background tasks.
     *
     * A second instance over the same Room database and six DataStore files is not a duplicate, it is
     * a corruption — DataStore raises `IllegalStateException` the moment two serve one file — so this
     * awaits a single `Deferred` exactly as [start] does, and for the same reason.
     */
    suspend fun ensureContainer(): IosAppContainer {
        container?.let { return it }
        val pending = containerBuild ?: scope.async { buildContainer() }.also { containerBuild = it }
        return pending.await()
    }

    private suspend fun buildContainer(): IosAppContainer {
        // The `User-Agent` has to be the one WebKit sends, or a `cf_clearance` earned in the sign-in
        // browser is rejected on the next API call, and WebKit will only say so asynchronously — so
        // the graph is built after that answer rather than around a guess. See `resolveWebKitUserAgent`.
        val graph =
            IosAppContainer(
                userAgent = resolveWebKitUserAgent(NodeSeekSite.CONFIG),
                offlineScheduler = offlineScheduler,
            )
        // Set before the first composition asks for an image, and once: `setSafe` is a no-op if
        // something already installed a loader, which is what makes a second call harmless.
        SingletonImageLoader.setSafe { context -> imageLoader(context, graph) }
        container = graph
        containerBuild = null
        return graph
    }

    private suspend fun buildController(): UIViewController {
        val graph = ensureContainer()
        val controller =
            ComposeUIViewController {
                NodysseyRoot(
                    container = graph,
                    // No notification extra and no deep link to read yet: both arrive through
                    // `UIApplicationDelegate`, and neither has an iOS half — the poll worker has no
                    // iOS counterpart and Universal Links have no association file. See
                    // `AppLinkHandling.ios.kt`.
                    initialTab = TopLevelDestination.HOME,
                    launchRequest = null,
                    onLaunchRequestHandled = {},
                )
            }
        root = controller
        // Nothing left to await; the fast path above answers from here on.
        controllerBuild = null
        // On screen now: schedule the daily sweep and resume any queue a past run left behind — the
        // iOS counterpart of what the Android `NodysseyApp` does in `onCreate`.
        offlineScheduler.onLaunch()
        return controller
    }

    private fun imageLoader(context: PlatformContext, container: IosAppContainer): ImageLoader =
        nodysseyImageLoader(context) { container.urlSession }
}
