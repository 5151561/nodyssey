@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.UserAgent
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * The Apple half of h1's 人机验证 block — the same page, hosted by `WKWebView`.
 *
 * The bridge is where the two platforms genuinely differ: Android hands JavaScript a Java object,
 * WebKit gives it a message handler on `window.webkit.messageHandlers`. A [WKUserScript] injected at
 * document start defines `__nsBridge` over that, so [turnstileDocument] does not have to know which
 * host it landed in.
 *
 * Contained the same way the Android actual is: the document is loaded from a string, and the
 * navigation delegate cancels every main-frame load, so nothing in here can become a browser.
 */
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
    val currentToken by rememberUpdatedState(onToken)
    val currentExpired by rememberUpdatedState(onExpired)
    val currentUnavailable by rememberUpdatedState(onUnavailable)
    val document = remember(sitekey, darkTheme) { turnstileDocument(sitekey, darkTheme) }

    val host =
        remember {
            TurnstileHost(
                onToken = { currentToken(it) },
                onExpired = { currentExpired() },
                onUnavailable = { currentUnavailable() },
            )
        }
    val webView =
        remember(document, userAgent) {
            newTurnstileWebView(userAgent, host).also {
                it.loadHTMLString(document, baseURL = NSURL(string = NodeSeekSite.BASE_URL + "/"))
            }
        }

    // Skips the first composition: the widget fetches a token on its own when it renders, and
    // resetting a box the user has not ticked yet would be a wasted round trip.
    LaunchedEffect(resetSignal) {
        if (resetSignal != 0) webView.evaluateJavaScript("nsTurnstileReset()", null)
    }

    UIKitView(
        factory = { webView },
        modifier = modifier,
        onRelease = { view ->
            view.stopLoading()
            view.configuration.userContentController.removeScriptMessageHandlerForName(TURNSTILE_BRIDGE)
        },
    )
}

private fun newTurnstileWebView(userAgent: UserAgent, host: TurnstileHost): WKWebView {
    val controller = WKUserContentController()
    controller.addScriptMessageHandler(host, TURNSTILE_BRIDGE)
    controller.addUserScript(
        WKUserScript(
            source = BRIDGE_SHIM,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            // The shim has to exist in the frame the widget's own script runs in, and only there.
            forMainFrameOnly = true,
        ),
    )
    val configuration = WKWebViewConfiguration()
    configuration.userContentController = controller
    configuration.websiteDataStore = WKWebsiteDataStore.defaultDataStore()
    configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
    val webView = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
    if (!userAgent.isWebViewDefault) webView.customUserAgent = userAgent.value
    // The strip sits on the form's own surface; an opaque white box would be a hole in it.
    webView.opaque = false
    webView.backgroundColor = UIColor.clearColor
    webView.scrollView.scrollEnabled = false
    webView.navigationDelegate = host
    return webView
}

/**
 * Message handler and navigation policy in one object.
 *
 * `NSObject` and the two protocols rather than lambdas, for the reason the sign-in screen's own
 * delegate gives: an Objective-C delegate is an object, and both of these are set on the same view.
 */
private class TurnstileHost(
    private val onToken: (String) -> Unit,
    private val onExpired: () -> Unit,
    private val onUnavailable: () -> Unit,
) : NSObject(),
    WKScriptMessageHandlerProtocol,
    WKNavigationDelegateProtocol {

    private var hasLoaded = false

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return onUnavailable()
        dispatchTurnstileMessage(
            kind = body["kind"] as? String ?: "",
            value = body["value"] as? String ?: "",
            onToken = onToken,
            onExpired = onExpired,
            onUnavailable = onUnavailable,
        )
    }

    /**
     * Exactly one main-frame load — the document that was handed in — and nothing after it.
     *
     * A count rather than a URL test: `loadHTMLString` navigates to the *base* URL, so "is this the
     * document we supplied?" and "is this somewhere the page decided to go?" look identical from the
     * request. The widget's own iframe is a subframe load and never reaches here.
     */
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val first = !hasLoaded
        hasLoaded = true
        decisionHandler(
            if (first) {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            },
        )
    }
}

/** Gives the page the same `__nsBridge.post(kind, value)` the Android host exposes directly. */
private val BRIDGE_SHIM =
    """
    window.$TURNSTILE_BRIDGE = {
      post: function (kind, value) {
        window.webkit.messageHandlers.$TURNSTILE_BRIDGE.postMessage({ kind: kind, value: value });
      }
    };
    """.trimIndent()
