package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.UserAgent

/**
 * h1's 人机验证 block, which is a web page however much the rest of the screen is not.
 *
 * Cloudflare's Turnstile is a script that draws its own widget and decides on its own terms whether
 * it trusts the client; there is no token endpoint to call and no way to render it from Compose. So
 * this is one web view about seventy points tall, showing nothing but the checkbox, with the token
 * bridged back to [SignInViewModel.onVerified]. The rest of the form stays native — which is the
 * whole point of h1, and the reason this is a component and not a return to the web sign-in page.
 *
 * `expect` for the same reason `WebViewRoute` is: Android's answer is a `WebView` and Apple's is a
 * `WKWebView`, and the JVM has neither. What the two share is [turnstileDocument] below — the page
 * itself is not a platform decision.
 *
 * @param resetSignal bump it to make the widget fetch a new token. A token is single-use and the
 *   site resets its own widget on every refusal, so the screen has to be able to say "that one is
 *   spent" without tearing the web view down and paying for a reload.
 * @param onUnavailable no web view here, or the widget could not start. The screen falls back to
 *   [VerificationState.NotWired], which says so and keeps 登录 disabled rather than sending a
 *   request the site is certain to refuse.
 */
@Composable
expect fun TurnstileWidget(
    sitekey: String,
    darkTheme: Boolean,
    userAgent: UserAgent,
    resetSignal: Int,
    onToken: (String) -> Unit,
    onExpired: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * The page both actuals load.
 *
 * Loaded with [NodeSeekSite.BASE_URL] as its base URL rather than from a file or `about:blank`, and
 * that is load-bearing: a sitekey is bound to the hostnames it may be rendered on, so a widget whose
 * document has no origin — or the wrong one — is refused before the user sees a checkbox.
 *
 * `render=explicit` with an `onload` callback, which is how the site's own page does it: the script
 * is `async`, so the callback has to exist before it arrives rather than after.
 *
 * `__nsBridge` is defined by whichever platform is hosting — a `@JavascriptInterface` object on
 * Android, a `WKUserScript` shim over `webkit.messageHandlers` on Apple. Same two arguments either
 * way, so this string does not know which one it got.
 */
internal fun turnstileDocument(sitekey: String, darkTheme: Boolean): String {
    val theme = if (darkTheme) "dark" else "light"
    return """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          html,body{margin:0;padding:0;background:transparent;overflow:hidden}
          #w{display:flex;justify-content:center;align-items:center;min-height:65px}
        </style>
        <script>
          function nsTurnstileReady() {
            try {
              turnstile.render('#w', {
                sitekey: '$sitekey',
                theme: '$theme',
                callback: function (t) { __nsBridge.post('token', t); },
                'expired-callback': function () { __nsBridge.post('expired', ''); },
                'timeout-callback': function () { __nsBridge.post('expired', ''); },
                'error-callback': function (e) { __nsBridge.post('error', String(e || '')); }
              });
            } catch (e) { __nsBridge.post('error', String(e)); }
          }
          function nsTurnstileReset() { try { turnstile.reset(); } catch (e) {} }
        </script>
        <script src="$TURNSTILE_SCRIPT" async defer></script>
        </head><body><div id="w"></div></body></html>
    """.trimIndent()
}

/** Cloudflare's own loader. The only script the widget's page pulls. */
internal const val TURNSTILE_SCRIPT =
    "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=nsTurnstileReady"

/** What a platform host calls when the page posts something back. */
internal fun dispatchTurnstileMessage(
    kind: String,
    value: String,
    onToken: (String) -> Unit,
    onExpired: () -> Unit,
    onUnavailable: () -> Unit,
) {
    when (kind) {
        "token" -> if (value.isNotBlank()) onToken(value) else onUnavailable()

        "expired" -> onExpired()

        // Cloudflare's own error codes are not worth telling apart here: every one of them ends with
        // the user having no token, and the screen has exactly one thing to say about that.
        else -> onUnavailable()
    }
}

/** The name the page calls the host object by. */
internal const val TURNSTILE_BRIDGE = "__nsBridge"

/** Roughly the widget's own drawn height (65 CSS px), plus room for its focus ring. */
internal const val TURNSTILE_HEIGHT_DP = 72
