package io.github.plaza.core.net

import android.content.Context
import android.webkit.WebSettings

/**
 * Asks the WebView what it is, rather than telling the site what we wish it were.
 *
 * This is the fix for the infinite Cloudflare checkbox. A managed challenge cross-checks the
 * `User-Agent` header against the JavaScript environment — `navigator.userAgentData` and the
 * `Sec-CH-UA` hints, which WebView derives from its *real* Chromium version and which
 * `setUserAgentString` does not touch. A header claiming "Chrome 126 on a Pixel 8" on a device
 * running something else is a contradiction, and a managed challenge answers a contradiction with
 * another challenge, forever.
 *
 * OkHttp then has to send the same string, because `cf_clearance` is issued against the UA that
 * solved the challenge and is rejected for any other. One source, both consumers.
 */
fun resolveUserAgent(context: Context, config: SiteConfig): UserAgent {
    // Throws on devices where the WebView provider is missing or mid-update. Not a suspend call, so
    // `runCatching` is safe here — there is no cancellation to swallow.
    val default =
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    return if (default != null) {
        UserAgent(value = default, isWebViewDefault = true)
    } else {
        // The WebView is unusable anyway; what matters is that both sides still agree.
        UserAgent(value = config.fallbackUserAgent, isWebViewDefault = false)
    }
}
