package io.github.nodyssey.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_open_in_external_browser
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.net.resolveUserAgent
import io.github.plaza.designsys.component.PlazaBackHandler
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful entry point: turns a [WebViewGoal] into the cookie condition the screen watches for.
 *
 * The turning is [webViewPolicy], in `commonMain` — none of what a goal decides is about Android, and
 * the iOS actual asks the same question.
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
        // A restored or malformed navigation key must fail closed. Authentication WebViews are never
        // allowed to become a general browser merely because a string reached the back stack.
        LaunchedEffect(url) {
            onOpenExternal(url)
            onClose()
        }
        return
    }

    val baseline = remember { session.peek() }
    val policy = remember(goal, session, baseline, isBound) { webViewPolicy(goal, session, baseline, isBound) }

    DisposableEffect(Unit) {
        // Publishing happens here and nowhere else. The whole session change — cache invalidation, the
        // feed reload — lands after this screen is gone, so nothing fires a request at NodeSeek while
        // the user is still working through a Cloudflare challenge.
        onDispose { session.sync() }
    }

    WebViewScreen(
        url = url,
        title = title,
        userAgent = userAgent,
        onOpenExternal = onOpenExternal,
        onClose = onClose,
        onCheckGoal = policy.onCheckGoal,
        pollIntervalMillis = policy.pollIntervalMillis,
        isInScope = policy.isInScope,
        canLeaveToBrowser = policy.canLeaveToBrowser,
        modifier = modifier,
    )
}

/**
 * The escape hatch for everything a plain HTTP client cannot do: signing in and clearing a
 * Cloudflare challenge. Cookies land in the shared [CookieManager], so closing this screen leaves
 * the rest of the app authenticated without any copying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewScreen(
    url: String,
    title: String,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Left alone when it is already the WebView's own — see [resolveUserAgent]. Overriding the UA
     * string is what stops Chromium reporting matching UA client hints, which is what made Cloudflare
     * re-challenge forever.
     */
    userAgent: UserAgent? = null,
    /**
     * Polled while the page is open; true once the cookie this screen was opened for has arrived, at
     * which point the screen closes itself.
     *
     * Polling rather than a callback because there is nothing to hook: NodeSeek signs in over an XHR,
     * so no navigation happens, `onPageFinished` never fires, and [CookieManager] has no listener.
     */
    onCheckGoal: (suspend () -> Boolean)? = null,
    /** How often [onCheckGoal] is asked. */
    pollIntervalMillis: Long = GOAL_POLL_MILLIS,
    /**
     * Which main-frame navigations belong to this screen. Everything else is a link the user
     * followed out, and is handed to the browser.
     */
    isInScope: (String) -> Boolean = NodeSeekSite::isTrustedWebViewUrl,
    /** Whether the toolbar offers a way out to a real browser; see the caller for why it is not always. */
    canLeaveToBrowser: Boolean = false,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var popup by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    // Where the user actually is, not where they came in — they may have followed links since, and
    // the handoff to the browser should carry the page they are looking at.
    var currentUrl by remember { mutableStateOf(url) }

    // Back dismisses the popup first — it is the topmost thing on screen, and the page underneath is
    // still where the user was.
    PlazaBackHandler(enabled = popup != null) { popup = null }
    PlazaBackHandler(enabled = popup == null && canGoBack) { webView?.goBack() }

    val autoReturn = onCheckGoal != null
    val checkGoal by rememberUpdatedState(onCheckGoal)
    val close by rememberUpdatedState(onClose)
    val openExternal by rememberUpdatedState(onOpenExternal)
    val staysHere by rememberUpdatedState(isInScope)

    LaunchedEffect(autoReturn) {
        if (!autoReturn) return@LaunchedEffect
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        configureForNodeSeek(
                            userAgent = userAgent,
                            staysHere = { staysHere(it) },
                            openExternal = { openExternal(it) },
                            onPageLoaded = { view ->
                                loading = false
                                canGoBack = view.canGoBack()
                                view.url?.let { currentUrl = it }
                            },
                            onOpenPopup = { child -> popup = child },
                            onClosePopup = { popup = null },
                        )
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { view ->
                    // Last chance to persist, and the only place the WebView gets torn down — one
                    // undestroyed WebView holds on to its whole rendering stack.
                    CookieManager.getInstance().flush()
                    webView = null
                    view.stopLoading()
                    view.destroy()
                },
            )
            // The popup window, when the page opened one. Telegram's login widget is the reason this
            // exists: it authorises in a `window.open` child and posts the result back to its opener,
            // so a WebView that quietly turns that into a same-window navigation loses the opener and
            // the binding never lands. Drawn over the page it belongs to, and dismissed by the same
            // `window.close()` the widget already calls.
            popup?.let { child ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { child },
                    onRelease = { view ->
                        CookieManager.getInstance().flush()
                        view.stopLoading()
                        view.destroy()
                    },
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
 * The one place a WebView on this screen is configured — the page itself and any popup it opens.
 *
 * Shared rather than copied because a popup with weaker settings is a security hole and a popup with
 * different ones is a bug: the Telegram authorisation window needs the same cookie jar, the same user
 * agent and the same idea of which hosts belong here as the page that opened it.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForNodeSeek(
    userAgent: UserAgent?,
    staysHere: (String) -> Boolean,
    openExternal: (String) -> Unit,
    onPageLoaded: (WebView) -> Unit,
    onOpenPopup: (WebView) -> Unit,
    onClosePopup: () -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.safeBrowsingEnabled = true
    // Off by default, which makes Android silently rewrite `window.open` into a same-window
    // navigation. `setJavaScriptCanOpenWindowsAutomatically` stays off: a popup is allowed because
    // the user tapped something, not because a script felt like it.
    settings.setSupportMultipleWindows(true)
    if (userAgent != null && !userAgent.isWebViewDefault) {
        settings.userAgentString = userAgent.value
    }
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    // Turnstile runs in an iframe served from challenges.cloudflare.com, so its cookies are
    // third-party ones. Block them and the checkbox never sticks.
    cookies.setAcceptThirdPartyCookies(this, true)
    // Cloudflare's interactive challenge expects a host that can answer for popups and console
    // output. Without one, some challenge variants silently restart.
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?,
        ): Boolean {
            val host = view ?: return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val child = WebView(host.context)
            child.configureForNodeSeek(
                userAgent = userAgent,
                staysHere = staysHere,
                openExternal = openExternal,
                // A popup carries no progress bar and cannot go back; only the page owns those.
                onPageLoaded = {},
                onOpenPopup = onOpenPopup,
                onClosePopup = onClosePopup,
            )
            onOpenPopup(child)
            transport.webView = child
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            onClosePopup()
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val target = request?.url?.toString() ?: return true
            if (!request.isForMainFrame || staysHere(target)) {
                return false
            }
            // User-controlled links and redirects leave the authenticated WebView. It retains
            // JavaScript and third-party cookies solely for NodeSeek login and Cloudflare
            // challenge pages.
            view?.post { openExternal(target) }
            return true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            view?.let(onPageLoaded)
            // Persist immediately: the user may leave right after logging in.
            CookieManager.getInstance().flush()
        }
    }
}
