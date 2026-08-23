package io.github.nodyssey.ui.login

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.UserAgent

/**
 * The Turnstile checkbox in a `WebView` the size of the checkbox.
 *
 * Contained on purpose, and each line of it matters. Nothing may navigate: the document is loaded
 * from a string and [WebViewClient.shouldOverrideUrlLoading] refuses every main-frame load, so a
 * script that tried to send this view somewhere gets nowhere. The `@JavascriptInterface` object
 * exposes one method taking two strings — the smallest surface that can carry a token — rather than
 * anything the page could use to reach the app.
 *
 * Third-party cookies stay on. Turnstile runs in an iframe from `challenges.cloudflare.com`, so its
 * cookies are third-party by definition and the checkbox never sticks without them; the sign-in web
 * view next door made the same call for the same reason.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun TurnstileWidget(
    sitekey: String,
    darkTheme: Boolean,
    userAgent: UserAgent,
    resetSignal: Int,
    onToken: (String) -> Unit,
    onExpired: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    // The page outlives a recomposition; the lambdas do not. Reading them through
    // `rememberUpdatedState` is what keeps a token arriving late from being handed to a stale one.
    val currentToken by rememberUpdatedState(onToken)
    val currentExpired by rememberUpdatedState(onExpired)
    val currentUnavailable by rememberUpdatedState(onUnavailable)
    val document = remember(sitekey, darkTheme) { turnstileDocument(sitekey, darkTheme) }
    // A plain box, not snapshot state: `update` runs in the apply phase, and writing state there to
    // remember what it already did would schedule a recomposition to say nothing.
    val lastReset = remember { intArrayOf(resetSignal) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Built here rather than remembered above: `factory` already runs exactly once, and the
            // callbacks it closes over are `rememberUpdatedState` reads, so they stay current on
            // their own. Keeping the construction at the call site is also what lets lint resolve
            // the argument to `addJavascriptInterface` — through `remember`'s type parameter it
            // sees only `T`, and reports the annotated method as missing.
            val bridge =
                TurnstileBridge(
                    onToken = { currentToken(it) },
                    onExpired = { currentExpired() },
                    onUnavailable = { currentUnavailable() },
                )
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                // A widget is not a browser: no popups, and nothing a script opens by itself.
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                if (!userAgent.isWebViewDefault) settings.userAgentString = userAgent.value
                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(bridge, TURNSTILE_BRIDGE)
                webViewClient =
                    object : WebViewClient() {
                        /** Nothing in this view navigates. The document it was given is all there is. */
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = true
                    }
                // The base URL is the sitekey's hostname check — see [turnstileDocument].
                loadDataWithBaseURL(
                    NodeSeekSite.BASE_URL + "/",
                    document,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        update = { view ->
            // Only ever a re-render, never a reload: `turnstile.reset()` asks Cloudflare for a fresh
            // token in the widget that is already there, which is what the site's own form does after
            // a refusal. A reload would cost the whole script fetch and drop the user back to an
            // unticked box for no reason.
            if (lastReset[0] != resetSignal) {
                view.evaluateJavascript("nsTurnstileReset()", null)
                lastReset[0] = resetSignal
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface(TURNSTILE_BRIDGE)
            view.stopLoading()
            // One undestroyed WebView holds on to its whole rendering stack — the same note the
            // sign-in screen's `onRelease` carries.
            view.destroy()
        },
    )
}

/**
 * The two strings the page is allowed to say, and nothing else.
 *
 * `@JavascriptInterface` methods arrive on a WebView-owned thread, so the hop to the main looper is
 * not optional: what these callbacks reach is a `ViewModel` and, through it, Compose state.
 */
private class TurnstileBridge(
    private val onToken: (String) -> Unit,
    private val onExpired: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun post(kind: String, value: String) {
        main.post {
            dispatchTurnstileMessage(
                kind = kind,
                value = value,
                onToken = onToken,
                onExpired = onExpired,
                onUnavailable = onUnavailable,
            )
        }
    }
}
