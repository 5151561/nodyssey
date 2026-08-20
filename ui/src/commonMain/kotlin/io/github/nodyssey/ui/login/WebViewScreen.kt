package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SessionState
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
 * gave up on sharing rather than found a shape. The reason is that Android's answer is a `WebView`
 * whose cookies land in the same `CookieManager` OkHttp reads, and Apple's is a `WKWebView` plus a
 * bridge between `WKHTTPCookieStore` and `NSHTTPCookieStorage` — a different object model with a
 * different lifetime, not the same view behind a parameter.
 *
 * That bridge exists now: [io.github.plaza.core.net.WebKitCookieBridge], step D3a of
 * `docs/kmp-migration-plan.md`. With a second implementation to compare against, the half that was
 * never about a platform came out of it — [webViewPolicy] below, which both actuals read. The view
 * itself stays `expect`, for the reason above.
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

/**
 * Everything a [WebViewGoal] decides, which is the half of this screen that is not a browser.
 *
 * In `commonMain` because none of it is: what closes the screen, how often to ask, which links belong
 * here and whether there is a way out to a real browser are all answers about a forum's errands. Two
 * platforms embed a web view now, and a `when` over the goals written twice is a `when` that gets
 * updated once — the compiler has nothing to say about the copy that was missed.
 *
 * The view itself stays `expect`, for the reason [WebViewRoute] gives.
 */
internal class WebViewPolicy(
    /** Polled while the page is open; true once the screen has what it was opened for. */
    val onCheckGoal: (suspend () -> Boolean)?,
    /** How often [onCheckGoal] is asked. */
    val pollIntervalMillis: Long,
    /** Which main-frame navigations belong to this screen; everything else is handed to the browser. */
    val isInScope: (String) -> Boolean,
    /** Whether the toolbar offers a way out to a real browser. */
    val canLeaveToBrowser: Boolean,
)

/**
 * @param baseline the session as it was when the screen opened.
 *
 * Auto-return fires on a *change*, not on presence, so a stale `cf_clearance` that was already in the
 * jar — and that is very likely why the request failed in the first place — cannot bounce the user
 * straight back out before they have done anything.
 */
internal fun webViewPolicy(
    goal: WebViewGoal,
    session: SessionRepository,
    baseline: SessionState,
    isBound: (suspend () -> Boolean)?,
): WebViewPolicy =
    WebViewPolicy(
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

            WebViewGoal.TELEGRAM_BIND -> isBound
        },
        // A poll that costs a request is paced for the server, not for the eye; the cookie goals above
        // read a local jar and can afford twice a second.
        pollIntervalMillis =
        if (goal == WebViewGoal.TELEGRAM_BIND) BINDING_POLL_MILLIS else GOAL_POLL_MILLIS,
        // 绑定 Telegram is the one errand on these pages that has to leave nodeseek.com and come back;
        // letting the detour out to the browser strands the return leg in a jar with no session.
        // Sign-in and challenge pages keep the narrower rule.
        isInScope =
        when (goal) {
            WebViewGoal.MANAGE, WebViewGoal.TELEGRAM_BIND -> {
                { target ->
                    NodeSeekSite.isTrustedWebViewUrl(target) || NodeSeekSite.isTelegramOAuthUrl(target)
                }
            }

            WebViewGoal.SIGN_IN, WebViewGoal.CHALLENGE -> NodeSeekSite::isTrustedWebViewUrl
        },
        // 管理 pages only. Now that every nodeseek.com link opens here rather than in a Custom Tab, a
        // page this view renders badly — or that the user would rather read with their browser's own
        // tools — would otherwise have no way out. The other three goals must not offer it: each one
        // exists to put a cookie in *this* jar, and finishing the errand in the browser would write it
        // somewhere the app cannot read, which is the failure this screen was built to avoid.
        canLeaveToBrowser = goal == WebViewGoal.MANAGE,
    )

/** Fast enough that the return feels immediate, slow enough to be free at 60fps. */
internal const val GOAL_POLL_MILLIS = 500L

/** One `/setting` fetch each; slow enough to sit politely behind a page the user is still reading. */
internal const val BINDING_POLL_MILLIS = 4_000L

/**
 * The beat between the goal being met and the screen closing.
 *
 * NodeSeek redirects to the front page right after a successful sign-in, and that navigation carries
 * the rest of the cookies. Waiting also stops the return from looking like a crash.
 */
internal const val GOAL_SETTLE_MILLIS = 500L
