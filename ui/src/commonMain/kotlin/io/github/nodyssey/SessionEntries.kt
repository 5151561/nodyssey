package io.github.nodyssey

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.login.SignInRoute
import io.github.nodyssey.ui.login.SignInViewModel
import io.github.nodyssey.ui.login.TelegramBindingViewModel
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.login.WebViewRoute

/**
 * The session's own screens: native sign-in, and the web view that shares its cookies.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.sessionEntries(nav: StackEntryScope) = with(nav) {
    entry<SignInKey> {
        val viewModel: SignInViewModel = viewModel(factory = SignInViewModel.factory(container))
        SignInRoute(
            viewModel = viewModel,
            // Both sites share one Turnstile application, but the key is still read per site — see
            // [Site.turnstileSitekey].
            sitekey = ActiveSite.current.turnstileSitekey,
            // The widget's web view has to look like the app's other requests, or Cloudflare
            // is grading a different client than the one that will send the token.
            userAgent = container.userAgent,
            onClose = { backStack.removeLastOrNull() },
            // The screen that asked for a session is underneath; dropping this one puts the
            // user back on it, and its own reload keys on the session generation.
            onSignedIn = { backStack.removeLastOrNull() },
            onUseWebSignIn = {
                backStack.removeLastOrNull()
                backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN))
            },
            // 忘记密码 and 还没有账号 both land on the site's own sign-in page, on top of this
            // screen rather than instead of it. Neither has a verified URL of its own, and a
            // guessed one would be a button that goes nowhere.
            onOpenSiteSignInPage = {
                backStack.add(WebKey(signInUrl, siteTitle, WebViewGoal.SIGN_IN))
            },
            // 一键登录, *instead of* this screen rather than on top of it — the same replacement
            // 改用网页登录 does above, and for the same reason. The web view closes itself the moment
            // a session lands, and a sign-in screen left underneath is what it would land back on:
            // still showing an empty credential form, still saying signed out, with the session it
            // is denying already in the jar. See [Site.oneTapSignIn].
            onOneTapSignIn = {
                ActiveSite.current.oneTapSignIn?.let { oneTap ->
                    backStack.removeLastOrNull()
                    backStack.add(
                        WebKey(
                            NodeSeekSite.BASE_URL + oneTap.path,
                            siteTitle,
                            WebViewGoal.ONE_TAP_SIGN_IN,
                        ),
                    )
                }
            },
        )
    }

    entry<WebKey> { key ->
        // What 绑定 Telegram polls while the page is open; the request and its "a failure means
        // not yet" policy live behind the ViewModel rather than in this lambda, which used to be
        // the one place the navigation layer issued a network call of its own.
        val bindingViewModel: TelegramBindingViewModel =
            viewModel(factory = TelegramBindingViewModel.factory(container))
        WebViewRoute(
            url = key.url,
            title = key.title,
            goal = key.goal,
            session = container.sessionRepository,
            userAgent = container.userAgent,
            onOpenExternal = openExternalUrl,
            onClose = { backStack.removeLastOrNull() },
            isBound = bindingViewModel::isBound,
        )
    }
}
