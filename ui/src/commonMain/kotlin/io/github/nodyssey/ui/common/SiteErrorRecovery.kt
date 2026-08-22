package io.github.nodyssey.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_sign_in
import io.github.nodyssey.ui.resources.action_verify
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.StatusAction
import org.jetbrains.compose.resources.stringResource

/*
 * The recovery half of a failure, for everywhere a full-screen [SiteErrorState] will not fit.
 *
 * [SiteErrorState] already knows that a Cloudflare wall is cleared by opening the web view and a
 * signed-out account by signing in. Every snackbar and inline strip re-derived that on its own, and
 * three of them settled on a hardcoded 重试 — which for a challenge is a button that cannot work:
 * retrying without clearing the wall earns the same wall, and the only control that would clear it
 * was on the screen the reader did not get. A cached feed therefore had no way out of the loop at
 * all, because the full-screen state only takes over when there is nothing cached to lose.
 *
 * So the rule lives here once, in the same shape [SiteErrorState] uses, and the short surfaces ask
 * for it instead of guessing.
 */

/**
 * Which button this failure deserves, or none when the reader cannot do anything about it.
 *
 * Null in three cases, and each is a button that would lie: a level wall no press can raise, a
 * request that was never sent, and any failure whose recovery the caller did not supply — a screen
 * with no way to reach the web view passes no [onVerify], and gets no 去验证.
 */
@Composable
fun siteErrorRecovery(
    error: SiteError,
    onVerify: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
): StatusAction? {
    val verify = stringResource(Res.string.action_verify)
    val signIn = stringResource(Res.string.action_sign_in)
    val retry = stringResource(Res.string.action_retry)
    return when (error) {
        SiteError.Cloudflare -> onVerify?.let { StatusAction(verify, it) }

        SiteError.LoginRequired -> onSignIn?.let { StatusAction(signIn, it) }

        // 阅读权限, "nothing was requested" and a term the site would not even search all fail the
        // same test: there is no press that changes the answer. See the matching branches in
        // [SiteErrorState].
        is SiteError.LevelRequired, SiteError.NotWired, SiteError.QueryTooShort -> null

        SiteError.Network,
        SiteError.Unparsable,
        SiteError.RateLimited,
        is SiteError.Http,
        SiteError.Unknown,
        -> onRetry?.let { StatusAction(retry, it) }
    }
}

/**
 * Says a failure once, with the button that clears it.
 *
 * For the screens that hold their failure in state and clear it on [onShown]. A feed whose failure
 * comes from Paging has no such state and keys on the message instead; it calls [siteErrorRecovery]
 * directly rather than going through here.
 *
 * [detail] is the site's own sentence when it sent one — 鸡腿不足, 对方已屏蔽你 — which always beats
 * our wording. A challenge never carries one: it is Cloudflare talking, not the forum.
 */
@Composable
fun SiteErrorSnackbar(
    error: SiteError?,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit,
    detail: String? = null,
    onVerify: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val fallback = error?.shortMessage()
    val action = error?.let { siteErrorRecovery(it, onVerify, onSignIn, onRetry) }
    LaunchedEffect(error, detail) {
        if (error == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = detail?.takeIf { it.isNotBlank() } ?: fallback.orEmpty(),
                actionLabel = action?.label,
                duration = snackbarDuration(error),
            )
        onShown()
        if (result == SnackbarResult.ActionPerformed) action?.onClick?.invoke()
    }
}

/**
 * How long a failure stays on screen.
 *
 * The two walls that need a human wait for one: a snackbar that times out takes the only control
 * that clears the wall with it, and the reader is back to a list that will not move with nothing to
 * press. Everything else is a passing failure and says so briefly.
 */
internal fun snackbarDuration(error: SiteError): SnackbarDuration =
    when (error) {
        SiteError.Cloudflare, SiteError.LoginRequired -> SnackbarDuration.Indefinite
        else -> SnackbarDuration.Short
    }
