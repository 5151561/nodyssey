package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.nodyssey.R
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.theme.LocalPlazaExtraColors
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.StatusShapes

/*
 * The states, wired to real copy.
 *
 * The skeleton they are all built from is `:designsys`'s [StatusView]: a blob, a title, a sentence
 * and up to two buttons, and no opinion about what any of them say. What lives here is the half that
 * cannot travel — which failure gets which icon, and every word, including the ones naming a board or
 * an account.
 */

/**
 * The same failures as one line, for somewhere a [StatusView] will not fit — a bottom sheet, a row.
 *
 * Shares the titles with the full-screen states so the two never drift into saying different things
 * about the same failure.
 */
@Composable
fun SiteError.shortMessage(): String =
    when (this) {
        SiteError.Cloudflare -> stringResource(R.string.status_challenge_title)

        SiteError.LoginRequired -> stringResource(R.string.status_sign_in_title)

        is SiteError.LevelRequired ->
            requiredLevel?.let { stringResource(R.string.status_level_required_title_level, it) }
                ?: stringResource(R.string.status_level_required_title)

        SiteError.Network -> stringResource(R.string.status_network_title)

        SiteError.Unparsable -> stringResource(R.string.status_unparsable_title)

        SiteError.RateLimited -> stringResource(R.string.status_rate_limited_title)

        is SiteError.Http -> stringResource(R.string.status_http_title, statusCode)

        SiteError.NotWired -> stringResource(R.string.status_not_wired_title)

        SiteError.Unknown -> stringResource(R.string.status_unknown_title)
    }

/**
 * Turns a data-layer failure into a screen.
 *
 * Most NodeSeek failures are not really errors — they are a human step the app cannot take: solve
 * the Cloudflare challenge, or sign in. Those get the action as the *primary* button and the retry
 * as the quiet one, because retrying without doing the thing will fail again.
 *
 * Picking the recovery action per error lives here, not at call sites: [onSignIn] and [onVerify]
 * exist so a screen never has to branch on the error itself — a hand-written
 * `if (LoginRequired) …` at every call site is exactly the copy that goes stale.
 */
@Composable
fun SiteErrorState(
    error: SiteError,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    boardTitle: String? = null,
    onBrowseElsewhere: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onVerify: (() -> Unit)? = null,
    /**
     * Leaves the screen. Only [SiteError.LevelRequired] uses it, and only when a caller passes one:
     * a level wall has no action that clears it, so the honest button is the way out, and a screen
     * that has nowhere to go back to (a tab root) shows none rather than a button that lies.
     */
    onBack: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalPlazaExtraColors.current
    val retry = StatusAction(stringResource(R.string.action_retry), onRetry)

    when (error) {
        SiteError.Cloudflare ->
            StatusView(
                icon = Icons.Default.CheckCircle,
                shape = StatusShapes.Challenge,
                containerColor = scheme.tertiaryContainer,
                iconColor = scheme.onTertiaryContainer,
                title = stringResource(R.string.status_challenge_title),
                description = stringResource(R.string.status_challenge_body),
                footnote = stringResource(R.string.status_challenge_footnote),
                primaryAction = StatusAction(stringResource(R.string.action_verify), onVerify ?: onOpenBrowser),
                secondaryAction = retry,
                modifier = modifier,
            )

        SiteError.LoginRequired ->
            StatusView(
                icon = Icons.Default.Lock,
                shape = StatusShapes.SignIn,
                containerColor = scheme.primaryContainer,
                iconColor = scheme.onPrimaryContainer,
                title =
                if (boardTitle.isNullOrBlank()) {
                    stringResource(R.string.status_sign_in_title)
                } else {
                    stringResource(R.string.status_sign_in_title_board, boardTitle)
                },
                description = stringResource(R.string.status_sign_in_body),
                primaryAction = StatusAction(stringResource(R.string.action_sign_in), onSignIn ?: onOpenBrowser),
                secondaryAction =
                onBrowseElsewhere?.let {
                    StatusAction(stringResource(R.string.status_sign_in_secondary), it)
                } ?: retry,
                modifier = modifier,
            )

        // 阅读权限, refused. No retry and no browser button: the same wall is on the web page, and
        // reloading it cannot raise a level. The site's own advice — 赚鸡腿升级 — is the description,
        // because it is advice rather than something a button here performs.
        is SiteError.LevelRequired ->
            StatusView(
                icon = Icons.Default.Lock,
                shape = StatusShapes.Locked,
                containerColor = extra.warningContainer,
                iconColor = extra.onWarningContainer,
                title =
                error.requiredLevel?.let {
                    stringResource(R.string.status_level_required_title_level, it)
                } ?: stringResource(R.string.status_level_required_title),
                description =
                error.requiredLevel?.let {
                    stringResource(R.string.status_level_required_body, it)
                } ?: stringResource(R.string.status_level_required_body_unknown),
                primaryAction =
                onBack?.let { StatusAction(stringResource(R.string.status_level_required_action), it) },
                modifier = modifier,
            )

        SiteError.Network ->
            StatusView(
                icon = Icons.Default.Warning,
                shape = StatusShapes.NetworkError,
                containerColor = scheme.errorContainer,
                iconColor = scheme.onErrorContainer,
                title = stringResource(R.string.status_network_title),
                description = stringResource(R.string.status_network_body),
                primaryAction = retry,
                modifier = modifier,
            )

        // The site changed shape under us. Retrying will not fix that, so the web view — which
        // renders whatever the new markup is — is the useful action.
        SiteError.Unparsable ->
            StatusView(
                icon = Icons.Default.Info,
                shape = StatusShapes.Deleted,
                containerColor = scheme.secondaryContainer,
                iconColor = scheme.onSecondaryContainer,
                title = stringResource(R.string.status_unparsable_title),
                description = stringResource(R.string.status_unparsable_body),
                primaryAction =
                StatusAction(stringResource(R.string.action_open_in_browser), onOpenBrowser),
                secondaryAction = retry,
                modifier = modifier,
            )

        // The site's own throttle, not Cloudflare's — so no verify button. Waiting is the whole fix,
        // and offering the web page would just spend another request against the same limit.
        SiteError.RateLimited ->
            StatusView(
                icon = Icons.Default.Info,
                shape = StatusShapes.NetworkError,
                containerColor = extra.warningContainer,
                iconColor = extra.onWarningContainer,
                title = stringResource(R.string.status_rate_limited_title),
                description = stringResource(R.string.status_rate_limited_body),
                primaryAction = retry,
                modifier = modifier,
            )

        is SiteError.Http ->
            StatusView(
                icon = Icons.Default.Warning,
                shape = StatusShapes.NetworkError,
                containerColor = extra.warningContainer,
                iconColor = extra.onWarningContainer,
                title = stringResource(R.string.status_http_title, error.statusCode),
                description = stringResource(R.string.status_generic_body),
                primaryAction = retry,
                secondaryAction =
                StatusAction(stringResource(R.string.action_open_in_browser), onOpenBrowser),
                modifier = modifier,
            )

        // Nothing was requested, so "重试" would be a lie: the only move is the site's own page.
        SiteError.NotWired -> NotWiredState(onOpenBrowser = onOpenBrowser, modifier = modifier)

        SiteError.Unknown ->
            StatusView(
                icon = Icons.Default.Warning,
                shape = StatusShapes.NetworkError,
                containerColor = scheme.errorContainer,
                iconColor = scheme.onErrorContainer,
                title = stringResource(R.string.status_unknown_title),
                description = stringResource(R.string.status_generic_body),
                primaryAction = retry,
                modifier = modifier,
            )
    }
}

@Composable
fun EmptyFeedState(
    modifier: Modifier = Modifier,
    onBrowseElsewhere: (() -> Unit)? = null,
) {
    StatusView(
        icon = Icons.Default.MailOutline,
        shape = StatusShapes.Empty,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = stringResource(R.string.status_empty_feed_title),
        description = stringResource(R.string.status_empty_feed_body),
        secondaryAction =
        onBrowseElsewhere?.let {
            StatusAction(stringResource(R.string.status_empty_feed_action), it)
        },
        modifier = modifier,
    )
}

/**
 * A ledger with no rows yet, which is a real state rather than a failure.
 *
 * A brand-new account has never earned a chicken, and plenty of accounts have never been given a
 * single stardust. Both would otherwise land on the generic feed empty state, whose copy invites the
 * reader to go browse another board — advice that has nothing to do with why this list is empty.
 */
@Composable
fun NoLedgerEntriesState(modifier: Modifier = Modifier) {
    StatusView(
        icon = PlazaIcons.Wallet,
        shape = StatusShapes.Empty,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = stringResource(R.string.status_empty_ledger_title),
        description = stringResource(R.string.status_empty_ledger_body),
        modifier = modifier,
    )
}

@Composable
fun DeletedContentState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusView(
        icon = Icons.Default.Delete,
        shape = StatusShapes.Deleted,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = stringResource(R.string.status_deleted_title),
        description = stringResource(R.string.status_deleted_body),
        secondaryAction = StatusAction(stringResource(R.string.status_deleted_action), onBack),
        modifier = modifier,
    )
}

@Composable
fun NoSearchResultsState(
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalPlazaExtraColors.current
    StatusView(
        icon = Icons.Default.Search,
        shape = StatusShapes.NoResults,
        containerColor = extra.warningContainer,
        iconColor = extra.onWarningContainer,
        title = stringResource(R.string.status_no_results_title),
        description = stringResource(R.string.status_no_results_body),
        secondaryAction = StatusAction(stringResource(R.string.status_no_results_action), onClearQuery),
        modifier = modifier,
    )
}

@Composable
fun SignedOutState(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusView(
        icon = Icons.Default.Face,
        shape = StatusShapes.Welcome,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        title = stringResource(R.string.status_signed_out_title),
        description = stringResource(R.string.status_signed_out_body),
        primaryAction = StatusAction(stringResource(R.string.action_sign_in), onSignIn),
        modifier = modifier,
    )
}

/**
 * Signed in, with the rest of the profile still to come.
 *
 * Deliberately not a celebration: the useful thing this screen can say today is that the session is
 * real and reversible. Claiming "登录后解锁完整体验" while already signed in was the bug.
 */
@Composable
fun SignedInState(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusView(
        icon = Icons.Default.CheckCircle,
        shape = StatusShapes.Welcome,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
        title = stringResource(R.string.status_signed_in_title),
        description = stringResource(R.string.status_signed_in_body),
        secondaryAction = StatusAction(stringResource(R.string.action_sign_out), onSignOut),
        modifier = modifier,
    )
}

/**
 * The site renders this page in the browser and exposes no endpoint we can read.
 *
 * Distinct from [ComingSoonState] on purpose: the screen exists and is designed, what is missing is
 * the data. Saying "还在做" would blame the wrong half and hide the one action that works today —
 * opening the same page in the web view, where the site's own JavaScript renders it.
 */
@Composable
fun NotWiredState(
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    StatusView(
        icon = Icons.Default.Info,
        shape = StatusShapes.Empty,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = stringResource(R.string.status_not_wired_title),
        description = description ?: stringResource(R.string.status_not_wired_body),
        primaryAction =
        StatusAction(stringResource(R.string.action_open_in_browser), onOpenBrowser),
        modifier = modifier,
    )
}

/** Honest placeholder for the tabs whose screens are not designed yet. */
@Composable
fun ComingSoonState(
    label: String,
    modifier: Modifier = Modifier,
) {
    StatusView(
        icon = Icons.Default.Build,
        shape = StatusShapes.Empty,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = stringResource(R.string.status_coming_soon_title, label),
        description = stringResource(R.string.status_coming_soon_body),
        modifier = modifier,
    )
}

// -------------------------------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Cloudflare")
@Composable
private fun ChallengeStatePreview() {
    PlazaTheme {
        SiteErrorState(error = SiteError.Cloudflare, onRetry = {}, onOpenBrowser = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Login required · dark")
@Composable
private fun SignInStatePreview() {
    PlazaTheme(darkTheme = true) {
        SiteErrorState(
            error = SiteError.LoginRequired,
            onRetry = {},
            onOpenBrowser = {},
            boardTitle = "内版",
            onBrowseElsewhere = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Level required")
@Composable
private fun LevelRequiredStatePreview() {
    PlazaTheme {
        SiteErrorState(
            error = SiteError.LevelRequired(requiredLevel = 5),
            onRetry = {},
            onOpenBrowser = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Network error")
@Composable
private fun NetworkStatePreview() {
    PlazaTheme {
        SiteErrorState(error = SiteError.Network, onRetry = {}, onOpenBrowser = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Empty feed")
@Composable
private fun EmptyFeedStatePreview() {
    PlazaTheme { EmptyFeedState(onBrowseElsewhere = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "No results")
@Composable
private fun NoResultsStatePreview() {
    PlazaTheme { NoSearchResultsState(onClearQuery = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Signed out")
@Composable
private fun SignedOutStatePreview() {
    PlazaTheme { SignedOutState(onSignIn = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Signed in")
@Composable
private fun SignedInStatePreview() {
    PlazaTheme { SignedInState(onSignOut = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Deleted")
@Composable
private fun DeletedStatePreview() {
    PlazaTheme { DeletedContentState(onBack = {}) }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Loading")
@Composable
private fun LoadingStatePreview() {
    PlazaTheme {
        Box(Modifier.fillMaxWidth()) { LoadingState() }
    }
}
