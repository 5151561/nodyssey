package io.github.nsreader.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.UserAgent
import io.github.nsreader.core.net.resolveUserAgent
import io.github.nsreader.data.session.SessionRepository
import kotlinx.coroutines.delay

/** Why the WebView was opened, which is also the condition for closing it again. */
enum class WebViewGoal {
    /** Close once NodeSeek has issued a session cookie. */
    SIGN_IN,

    /** Close once Cloudflare has issued a clearance cookie. */
    CHALLENGE,

    /**
     * Stays open until the user leaves it.
     *
     * For the authenticated pages the app deliberately does not reimplement — account settings, the
     * stardust transfer layer, buying an invite code. There is no cookie to wait for: the user is done
     * when they say so, and auto-closing mid-form would lose what they typed.
     */
    MANAGE,
}

/**
 * Stateful entry point: turns a [WebViewGoal] into the cookie condition the screen watches for.
 *
 * The baseline matters. Auto-return fires on a *change*, not on presence, so a stale `cf_clearance`
 * that was already in the jar — and that is very likely why the request failed in the first place —
 * cannot bounce the user straight back out before they have done anything.
 */
@Composable
fun WebViewRoute(
    url: String,
    title: String,
    goal: WebViewGoal,
    session: SessionRepository,
    userAgent: UserAgent,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
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
        // `peek` rather than `sync`: this runs twice a second, and it must observe without announcing.
        onCheckGoal =
        when (goal) {
            WebViewGoal.SIGN_IN -> {
                {
                    val state = session.peek()
                    state.isSignedIn && state.fingerprint != baseline.fingerprint
                }
            }

            WebViewGoal.CHALLENGE -> {
                {
                    val state = session.peek()
                    state.hasClearance && state.fingerprint != baseline.fingerprint
                }
            }

            WebViewGoal.MANAGE -> null
        },
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
    onCheckGoal: (() -> Boolean)? = null,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    val autoReturn = onCheckGoal != null
    val checkGoal by rememberUpdatedState(onCheckGoal)
    val close by rememberUpdatedState(onClose)
    val openExternal by rememberUpdatedState(onOpenExternal)

    LaunchedEffect(autoReturn) {
        if (!autoReturn) return@LaunchedEffect
        while (true) {
            delay(GOAL_POLL_MILLIS)
            if (checkGoal?.invoke() == true) {
                // NodeSeek redirects to the front page right after a successful sign-in, and that
                // navigation carries the rest of the cookies. Waiting a beat also stops the return
                // from looking like a crash.
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
                            contentDescription = stringResource(R.string.action_close),
                        )
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
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        if (userAgent != null && !userAgent.isWebViewDefault) {
                            settings.userAgentString = userAgent.value
                        }
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        // Turnstile runs in an iframe served from challenges.cloudflare.com, so its
                        // cookies are third-party ones. Block them and the checkbox never sticks.
                        cookies.setAcceptThirdPartyCookies(this, true)
                        // Cloudflare's interactive challenge expects a host that can answer for popups
                        // and console output. Without one, some challenge variants silently restart.
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString() ?: return true
                                if (!request.isForMainFrame || NodeSeekSite.isTrustedWebViewUrl(target)) {
                                    return false
                                }
                                // User-controlled links and redirects leave the authenticated
                                // WebView. It retains JavaScript and third-party cookies solely for
                                // NodeSeek login and Cloudflare challenge pages.
                                view?.post { openExternal(target) }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                canGoBack = view?.canGoBack() == true
                                // Persist immediately: the user may leave right after logging in.
                                CookieManager.getInstance().flush()
                            }
                        }
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

/** Fast enough that the return feels immediate, slow enough to be free at 60fps. */
private const val GOAL_POLL_MILLIS = 500L

private const val GOAL_SETTLE_MILLIS = 500L
