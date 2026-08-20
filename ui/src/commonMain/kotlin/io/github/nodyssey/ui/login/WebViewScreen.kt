package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.net.UserAgent

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

    /**
     * 绑定 Telegram: [MANAGE], plus it closes itself once the account actually carries a binding.
     *
     * The condition is not a cookie, so unlike the two above it costs a request per poll — which is
     * why it is its own goal rather than something [MANAGE] always does. It earns that: the errand
     * ends at Telegram's own confirmation, a screen with no reason to know it should send the user
     * back to an Android app, so without this the user is left holding a finished web page.
     */
    TELEGRAM_BIND,
}

/**
 * The escape hatch for everything a plain HTTP client cannot do: signing in and clearing a
 * Cloudflare challenge.
 *
 * `expect` for the whole screen rather than a seam inside it, which is the one place in step D1 that
 * gave up on sharing rather than found a shape. The reason is that there is nothing yet to share
 * *with*: Android's answer is a `WebView` whose cookies land in the same `CookieManager` OkHttp reads,
 * and Apple's will be a `WKWebView` plus a bridge between `WKHTTPCookieStore` and
 * `NSHTTPCookieStorage` — a different object model with a different lifetime, not the same view
 * behind a parameter. Writing one seam against a single implementation would be guessing at the
 * second. That bridge is step D3 of `docs/kmp-migration-plan.md`, and the toolbar and the goal
 * polling are worth revisiting for sharing once it exists.
 *
 * [isBound] answers "has the binding landed yet?" for [WebViewGoal.TELEGRAM_BIND]. Supplied by the
 * caller because this screen has no repositories of its own — and left null for every other goal,
 * which closes on a cookie instead.
 */
@Composable
expect fun WebViewRoute(
    url: String,
    title: String,
    goal: WebViewGoal,
    session: SessionRepository,
    userAgent: UserAgent,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isBound: (suspend () -> Boolean)? = null,
)
