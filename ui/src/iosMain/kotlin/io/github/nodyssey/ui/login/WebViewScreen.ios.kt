@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.UIKitView
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_open_in_external_browser
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.net.WebKitCookieBridge
import io.github.plaza.designsys.component.PlazaBackHandler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

/**
 * The iOS half of the escape hatch: a `WKWebView`, plus the bridge that makes its cookies visible to
 * the rest of the app.
 *
 * The bridge is the whole difference from Android, and it is why this is an `expect` rather than a
 * shared screen. There, one `CookieManager` is both what the browser writes and what OkHttp reads, so
 * a session arrives and nothing has to be copied. Here the browser writes `WKHTTPCookieStore` and
 * `NSURLSession` reads [platform.Foundation.NSHTTPCookieStorage], and
 * [WebKitCookieBridge] is what keeps the second one true while the first is being written to — see
 * its KDoc for what is mirrored and what deliberately is not.
 *
 * Everything a [WebViewGoal] decides is [webViewPolicy], shared with the Android actual.
 */
@Composable
actual fun WebViewRoute(
    url: String,
    title: String,
    goal: WebViewGoal,
    session: SessionRepository,
    userAgent: UserAgent,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    isBound: (suspend () -> Boolean)?,
) {
    if (!NodeSeekSite.isTrustedWebViewUrl(url)) {
        // A restored or malformed navigation key must fail closed, the same as on Android.
        LaunchedEffect(url) {
            onOpenExternal(url)
            onClose()
        }
        return
    }

    val baseline = remember { session.peek() }
    val policy = remember(goal, session, baseline, isBound) { webViewPolicy(goal, session, baseline, isBound) }
    val bridge =
        remember { WebKitCookieBridge(WKWebsiteDataStore.defaultDataStore().httpCookieStore) }
    val scope = rememberCoroutineScope()

    DisposableEffect(bridge) {
        // Publishing happens here and nowhere else, exactly as on Android: the feed reload lands after
        // this screen is gone rather than while a challenge is still being solved.
        onDispose {
            bridge.stop()
            session.sync()
        }
    }

    /**
     * Closes by way of one last awaited copy out of WebKit.
     *
     * The observer has been mirroring all along, so this is only the change that landed in the last
     * instant — but that instant is exactly when a sign-in finishes. `onDispose` cannot wait for
     * anything, which is why the wait happens on the way out rather than on the way down.
     */
    val closeAfterDrain: () -> Unit = {
        scope.launch {
            bridge.drain()
            onClose()
        }
        Unit
    }

    WebViewScreen(
        url = url,
        title = title,
        userAgent = userAgent,
        bridge = bridge,
        onOpenExternal = onOpenExternal,
        onClose = closeAfterDrain,
        onCheckGoal = policy.onCheckGoal,
        pollIntervalMillis = policy.pollIntervalMillis,
        isInScope = policy.isInScope,
        canLeaveToBrowser = policy.canLeaveToBrowser,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewScreen(
    url: String,
    title: String,
    userAgent: UserAgent,
    bridge: WebKitCookieBridge,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onCheckGoal: (suspend () -> Boolean)?,
    pollIntervalMillis: Long,
    isInScope: (String) -> Boolean,
    canLeaveToBrowser: Boolean,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<WKWebView?>(null) }
    // Where the user actually is, not where they came in.
    var currentUrl by remember { mutableStateOf(url) }

    val close by rememberUpdatedState(onClose)
    val openExternal by rememberUpdatedState(onOpenExternal)
    val staysHere by rememberUpdatedState(isInScope)
    val checkGoal by rememberUpdatedState(onCheckGoal)

    val delegate =
        remember {
            NodeSeekWebViewDelegate(
                staysHere = { staysHere(it) },
                openExternal = { openExternal(it) },
                onPageLoaded = { view ->
                    loading = false
                    canGoBack = view.canGoBack
                    view.URL?.absoluteString?.let { currentUrl = it }
                },
                onOpenPopup = { child -> popup = child },
                onClosePopup = { popup = null },
            )
        }

    val webView = remember { newWebView(userAgent, delegate) }

    // Back dismisses the popup first — it is the topmost thing on screen.
    PlazaBackHandler(enabled = popup != null) { popup = null }
    PlazaBackHandler(enabled = popup == null && canGoBack) { webView.goBack() }

    LaunchedEffect(webView) {
        // Seeded before the first load, and awaited: a page that starts before its cookies arrive is a
        // page loaded as a stranger, and a Cloudflare challenge answered as a stranger clears nothing.
        bridge.seed()
        bridge.start()
        NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest(uRL = it)) }
    }

    LaunchedEffect(pollIntervalMillis, checkGoal != null) {
        if (checkGoal == null) return@LaunchedEffect
        while (true) {
            delay(pollIntervalMillis)
            if (checkGoal?.invoke() == true) {
                delay(GOAL_SETTLE_MILLIS)
                close()
                return@LaunchedEffect
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.action_close),
                        )
                    }
                },
                actions = {
                    if (canLeaveToBrowser) {
                        IconButton(onClick = { openExternal(currentUrl) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription =
                                stringResource(Res.string.action_open_in_external_browser),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            UIKitView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> view.stopLoading() },
            )
            // The popup window, when the page opened one. Telegram's login widget is the reason it
            // exists: it authorises in a `window.open` child and posts the result back to its opener.
            popup?.let { child ->
                UIKitView(
                    factory = { child },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view -> view.stopLoading() },
                )
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * The one place a `WKWebView` on this screen is configured — the page and any popup it opens.
 *
 * Shared rather than copied for the same reason the Android side says so: the Telegram authorisation
 * window needs the same cookie jar, the same user agent and the same idea of which hosts belong here
 * as the page that opened it.
 *
 * `defaultDataStore` and not a non-persistent one: it is the jar [WebKitCookieBridge] mirrors, and a
 * private store would leave the session behind when the screen closes.
 */
private fun newWebView(
    userAgent: UserAgent,
    delegate: NodeSeekWebViewDelegate,
    configuration: WKWebViewConfiguration = WKWebViewConfiguration(),
): WKWebView {
    configuration.websiteDataStore = WKWebsiteDataStore.defaultDataStore()
    // A popup is allowed because the user tapped something, not because a script felt like it — the
    // same line the Android actual draws with `setJavaScriptCanOpenWindowsAutomatically`.
    configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
    val webView = WKWebView(frame = platform.CoreGraphics.CGRectZero.readValue(), configuration = configuration)
    if (!userAgent.isWebViewDefault) {
        webView.customUserAgent = userAgent.value
    }
    webView.navigationDelegate = delegate
    webView.UIDelegate = delegate
    return webView
}

/**
 * Navigation policy and popup handling in one object, which is what WebKit's two delegates come to.
 *
 * `NSObject` and the two protocols rather than lambdas: an Objective-C delegate is an object, and both
 * of these are set on the same web view.
 */
private class NodeSeekWebViewDelegate(
    private val staysHere: (String) -> Boolean,
    private val openExternal: (String) -> Unit,
    private val onPageLoaded: (WKWebView) -> Unit,
    private val onOpenPopup: (WKWebView) -> Unit,
    private val onClosePopup: () -> Unit,
) : NSObject(),
    WKNavigationDelegateProtocol,
    WKUIDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val target = decidePolicyForNavigationAction.request.URL?.absoluteString
        val mainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame ?: true
        when {
            target == null -> decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)

            !mainFrame || staysHere(target) ->
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)

            else -> {
                // User-controlled links and redirects leave the authenticated web view. It keeps
                // JavaScript and third-party cookies solely for NodeSeek login and Cloudflare pages.
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                openExternal(target)
            }
        }
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onPageLoaded(webView)
    }

    /**
     * `window.open`, which WebKit turns into a request for a second web view.
     *
     * Returning null is what tells the page it was blocked, and blocking it is what broke 绑定
     * Telegram on Android before `setSupportMultipleWindows`: the widget authorises in a child window
     * and posts the result back to its opener.
     */
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        val child =
            WKWebView(
                frame = platform.CoreGraphics.CGRectZero.readValue(),
                configuration = createWebViewWithConfiguration,
            )
        child.customUserAgent = webView.customUserAgent
        child.navigationDelegate = this
        child.UIDelegate = this
        onOpenPopup(child)
        return child
    }

    /** The `window.close()` the widget calls when it is done. */
    override fun webViewDidClose(webView: WKWebView) {
        onClosePopup()
    }
}
