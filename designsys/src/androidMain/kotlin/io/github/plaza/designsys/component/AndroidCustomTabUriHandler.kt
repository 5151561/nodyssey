package io.github.plaza.designsys.component

import android.content.ComponentName
import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme

/**
 * A [UriHandler] that opens links in a Custom Tab, meant to be swapped in over the platform one at
 * the composition root.
 *
 * The file is named for its platform because all of it is: Custom Tabs are `androidx.browser`, which
 * is Android-only, and there is no cross-platform notion of "the browser, in this task". What is
 * portable is the seam rather than the implementation — [UriHandler] and `LocalUriHandler` are
 * Compose's own, so a platform that wants `SFSafariViewController` instead provides its own handler
 * over the same local and every `openUri` call site is already pointing at it.
 *
 * A forum thread is mostly other people's links, and every one of them otherwise starts a browser
 * task: read three links out of a thread and the app is three task switches behind whatever the
 * launcher shows. A Custom Tab is the browser doing the browsing — its process, its cookies, its
 * origin bar — but stacked on this task, so back lands on the thread it came from.
 *
 * Replacing the CompositionLocal rather than threading an opener through the screens is deliberate:
 * every call site that already says `LocalUriHandler.current.openUri(...)` is a link leaving the
 * app, including the ones Compose itself resolves for a `LinkAnnotation` inside post text. One
 * provider covers them all and nothing can be forgotten later.
 *
 * [shouldUseCustomTab] is what decides, per URI. It has to be the caller's: whether the feature is
 * even switched on is an app setting, and Custom Tabs speak http(s) only, so `mailto:`, `tg://` and
 * `otpauth://` belong to whichever app claimed the scheme. Everything it declines goes to
 * [fallback], which is also where a failed launch lands — and which still throws when nothing on the
 * device can take the link, because callers such as a 两步验证 screen read that failure to tell the
 * user so.
 *
 * The intent is built per launch rather than once, because [session] is not known until the browser
 * has answered the bind — see [CustomTabsWarmer]. Building one is assembling an `Intent` and a
 * handful of extras, which costs nothing next to the launch it precedes.
 */
class CustomTabUriHandler(
    private val context: Context,
    private val fallback: UriHandler,
    private val session: () -> CustomTabsSession?,
    private val customTabsIntent: (CustomTabsSession?) -> CustomTabsIntent,
    private val shouldUseCustomTab: (String) -> Boolean,
) : UriHandler {
    override fun openUri(uri: String) {
        if (shouldUseCustomTab(uri)) {
            // A browser without Custom Tabs support ignores the extras and opens a normal tab, so
            // no availability probe is needed — and probing would need a <queries> entry to work at
            // all on API 30+. runCatching covers the other outcome: nothing resolved the intent.
            val launched =
                runCatching { customTabsIntent(session()).launchUrl(context, uri.toUri()) }.isSuccess
            if (launched) return
        }
        fallback.openUri(uri)
    }
}

/**
 * Holds a binding to the default browser's Custom Tabs service for as long as the app is on screen.
 *
 * This is the whole of "why is a link slow in the app when it is instant in the browser". Opening
 * the browser directly finds a process that is already running with a warm network stack; a Custom
 * Tab launched out of nowhere has to cold-start that process first, and only once it exists does the
 * DNS lookup for the link start. `warmup` moves all of it before the tap: the browser starts its
 * process, brings up its network stack and keeps a renderer spare, and the binding is what stops the
 * system reclaiming any of it again the moment the tab is dismissed.
 *
 * The session matters as much as the warmup. A tab launched without one is anonymous to the browser,
 * so the work [mayLaunch] asked for cannot be matched to the page that then opens; passing the same
 * session to both is what lets the browser answer the launch with a connection — or a whole
 * prerender — it already has.
 *
 * Bound on `ON_START` and released on `ON_STOP` rather than held for the process's life: what this
 * keeps alive is *another app's* process, and doing that while the reader is somewhere else entirely
 * is not ours to do. Launching a tab stops this activity, which unbinds — by then the browser is the
 * thing in the foreground and needs no help from us — and coming back binds and warms again.
 */
class CustomTabsWarmer(
    private val context: Context,
) {
    private var connection: CustomTabsServiceConnection? = null

    /** The current session, or null while nothing is bound. Read at launch time, never cached. */
    var session: CustomTabsSession? = null
        private set

    fun connect() {
        if (connection != null) return
        // Null when no installed browser offers the service at all, which on API 30+ also covers
        // "the manifest did not declare the <queries> entry that makes them visible".
        val browser = runCatching { CustomTabsClient.getPackageName(context, null) }.getOrNull() ?: return
        val binding =
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    name: ComponentName,
                    client: CustomTabsClient,
                ) {
                    // Zero is the documented "no flags" argument; the parameter is reserved and has
                    // never had a value to pass.
                    runCatching { client.warmup(0L) }
                    session = runCatching { client.newSession(null) }.getOrNull()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    session = null
                }
            }
        connection = binding
        // A false return still leaves the connection registered with the system, so it is released
        // the same way a successful one is — otherwise the next connect() binds a second time and
        // the first is a leak the platform logs about.
        if (!runCatching { CustomTabsClient.bindCustomTabsService(context, browser, binding) }.getOrDefault(false)) {
            disconnect()
        }
    }

    fun disconnect() {
        val binding = connection ?: return
        connection = null
        session = null
        runCatching { context.unbindService(binding) }
    }

    /**
     * Asks the browser to get ready for [url] — resolve the host, open the connection, and, if it
     * decides the hint is worth it, render the page ahead of being asked for it.
     *
     * Silently nothing when no browser is bound yet, which is the first fraction of a second after
     * the app comes back and is not worth an error path: the link still opens, merely at the speed
     * it opened at before any of this existed.
     */
    fun mayLaunch(url: String) {
        val current = session ?: return
        runCatching { current.mayLaunchUrl(url.toUri(), null, null) }
    }
}

/**
 * Builds the handler above against the theme that is actually on screen, and the warm connection it
 * launches through.
 *
 * The toolbar reads from [MaterialTheme] rather than a fixed brand colour so the tab matches the app
 * it opened from, including 动态取色. `setColorScheme` is pinned to the app's own resolved
 * [LocalPlazaDarkTheme] instead of `COLOR_SCHEME_SYSTEM`: 深色 and 定时 make the app dark while
 * the system stays light, and a white toolbar over a dark app is exactly the seam a Custom Tab is
 * supposed to hide.
 *
 * The application context, not the composition's: the binding outlives any one activity instance and
 * has to be released against the same context it was taken against.
 */
@Composable
actual fun rememberBrowserLinks(shouldUseCustomTab: (String) -> Boolean): BrowserLinks {
    val context = LocalContext.current
    val fallback = LocalUriHandler.current
    val darkTheme = LocalPlazaDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme

    val warmer = remember(context) { CustomTabsWarmer(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, warmer) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> warmer.connect()
                    Lifecycle.Event.ON_STOP -> warmer.disconnect()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            warmer.disconnect()
        }
    }

    val customTabsIntent =
        remember(darkTheme, colorScheme) {
            { session: CustomTabsSession? -> customTabsIntent(session, darkTheme, colorScheme) }
        }
    return remember(context, fallback, customTabsIntent, shouldUseCustomTab, warmer) {
        BrowserLinks(
            uriHandler =
            CustomTabUriHandler(
                context = context,
                fallback = fallback,
                session = warmer::session,
                customTabsIntent = customTabsIntent,
                shouldUseCustomTab = shouldUseCustomTab,
            ),
            // Gated on the same answer the launch is: a URL that will not open in a Custom Tab has
            // no session to be warmed through, and one belonging to another app's scheme has no
            // page behind it at all.
            prefetcher = { url -> if (shouldUseCustomTab(url)) warmer.mayLaunch(url) },
        )
    }
}

private fun customTabsIntent(
    session: CustomTabsSession?,
    darkTheme: Boolean,
    colorScheme: ColorScheme,
): CustomTabsIntent =
    CustomTabsIntent
        .Builder(session)
        .setShowTitle(true)
        // The share sheet the browser already has. No custom menu item is added for it, and
        // none for "在浏览器中打开" either — the tab's own overflow menu carries that.
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .setColorScheme(
            if (darkTheme) {
                CustomTabsIntent.COLOR_SCHEME_DARK
            } else {
                CustomTabsIntent.COLOR_SCHEME_LIGHT
            },
        ).setDefaultColorSchemeParams(
            CustomTabColorSchemeParams
                .Builder()
                .setToolbarColor(colorScheme.surface.toArgb())
                .setSecondaryToolbarColor(colorScheme.surfaceContainer.toArgb())
                .setNavigationBarColor(colorScheme.surface.toArgb())
                .setNavigationBarDividerColor(colorScheme.outlineVariant.toArgb())
                .build(),
        ).build()
