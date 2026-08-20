package io.github.plaza.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.WebKit.WKWebView
import kotlin.coroutines.resume

/**
 * Asks WebKit what it is, rather than telling the site what we wish it were.
 *
 * The Android counterpart is [resolveUserAgent] in `androidMain`, and its argument is the whole
 * argument here: a managed Cloudflare challenge cross-checks the `User-Agent` header against the
 * JavaScript environment, and `cf_clearance` is issued against the UA that solved the challenge and
 * rejected for any other. The browser that solves it on this platform is a `WKWebView`, so what
 * `NSURLSession` sends has to be that view's string.
 *
 * **Asynchronous, where Android's is not**, and that is what this seam costs. `WebSettings` has a
 * static getter; WebKit has no supported synchronous one — the user agent is a property of a running
 * web content process, and the way to read it is to ask a page. So the shell resolves this once at
 * launch and builds its graph afterwards, rather than starting with a guess and correcting it: a
 * corrected user agent is a `cf_clearance` thrown away.
 */
suspend fun resolveWebKitUserAgent(config: SiteConfig): UserAgent {
    val fromWebKit =
        suspendCancellableCoroutine { continuation ->
            // Never added to a window: `evaluateJavaScript` starts a web content process on its own,
            // and `navigator.userAgent` needs nothing rendered.
            WKWebView().evaluateJavaScript("navigator.userAgent") { value, _ ->
                continuation.resume((value as? String)?.takeIf { it.isNotBlank() })
            }
        }

    return if (fromWebKit != null) {
        UserAgent(value = fromWebKit, isWebViewDefault = true)
    } else {
        // WebKit is unusable anyway; what matters is that both sides still agree.
        UserAgent(value = config.fallbackUserAgent, isWebViewDefault = false)
    }
}
