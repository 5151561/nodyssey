package io.github.nsreader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.ui.theme.LocalNodeSeekExtraColors
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.StatusShapes

/**
 * Every empty, error and blocked state in the app is this one composable.
 *
 * They share a shape language on purpose: a tonal blob, an icon, a sentence saying what happened
 * and a button that does something about it. "出错了 :(" is not a state — if there is nothing the
 * user can press, the screen has failed twice.
 */
data class StatusAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun StatusView(
    icon: ImageVector,
    shape: Shape,
    containerColor: Color,
    iconColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    footnote: String? = null,
    primaryAction: StatusAction? = null,
    secondaryAction: StatusAction? = null,
) {
    // Centred when it fits, scrollable when it does not. A status screen is the last thing that
    // should break at 200% font scale — it is often the only thing on screen.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 44.dp, vertical = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                Modifier
                    .size(116.dp)
                    .clip(shape)
                    .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    // The blob is decoration; the title next to it already says what the state is.
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(52.dp),
                )
            }
            Text(
                text = title,
                fontSize = 19.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                    Modifier
                        .padding(top = Spacing.sm)
                        .widthIn(max = Sizes.readableContentWidth),
                )
            }
            primaryAction?.let {
                Button(
                    onClick = it.onClick,
                    modifier =
                    Modifier
                        .padding(top = 28.dp)
                        .height(Sizes.minTouchTarget),
                ) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
                }
            }
            secondaryAction?.let {
                TextButton(
                    onClick = it.onClick,
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge)
                }
            }
            footnote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xl),
                )
            }
        }
    }
}

/** Full-screen spinner. Lists use a skeleton instead — a fixed structure fakes faster than a spinner. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// -------------------------------------------------------------------------------------------------
// The eight states, wired to real copy
// -------------------------------------------------------------------------------------------------

/**
 * The same failures as one line, for somewhere a [StatusView] will not fit — a bottom sheet, a row.
 *
 * Shares the titles with the full-screen states so the two never drift into saying different things
 * about the same failure.
 */
@Composable
fun NodeSeekError.shortMessage(): String =
    when (this) {
        NodeSeekError.Cloudflare -> stringResource(R.string.status_challenge_title)
        NodeSeekError.LoginRequired -> stringResource(R.string.status_sign_in_title)
        NodeSeekError.Network -> stringResource(R.string.status_network_title)
        NodeSeekError.Unparsable -> stringResource(R.string.status_unparsable_title)
        is NodeSeekError.Http -> stringResource(R.string.status_http_title, statusCode)
        NodeSeekError.Unknown -> stringResource(R.string.status_unknown_title)
    }

/**
 * Turns a data-layer failure into a screen.
 *
 * Most NodeSeek failures are not really errors — they are a human step the app cannot take: solve
 * the Cloudflare challenge, or sign in. Those get the action as the *primary* button and the retry
 * as the quiet one, because retrying without doing the thing will fail again.
 */
@Composable
fun NodeSeekErrorState(
    error: NodeSeekError,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    boardTitle: String? = null,
    onBrowseElsewhere: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalNodeSeekExtraColors.current
    val retry = StatusAction(stringResource(R.string.action_retry), onRetry)

    when (error) {
        NodeSeekError.Cloudflare ->
            StatusView(
                icon = Icons.Default.CheckCircle,
                shape = StatusShapes.Challenge,
                containerColor = scheme.tertiaryContainer,
                iconColor = scheme.onTertiaryContainer,
                title = stringResource(R.string.status_challenge_title),
                description = stringResource(R.string.status_challenge_body),
                footnote = stringResource(R.string.status_challenge_footnote),
                primaryAction = StatusAction(stringResource(R.string.action_verify), onOpenBrowser),
                secondaryAction = retry,
                modifier = modifier,
            )

        NodeSeekError.LoginRequired ->
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
                primaryAction = StatusAction(stringResource(R.string.action_sign_in), onOpenBrowser),
                secondaryAction =
                onBrowseElsewhere?.let {
                    StatusAction(stringResource(R.string.status_sign_in_secondary), it)
                } ?: retry,
                modifier = modifier,
            )

        NodeSeekError.Network ->
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
        NodeSeekError.Unparsable ->
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

        is NodeSeekError.Http ->
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

        NodeSeekError.Unknown ->
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
    val extra = LocalNodeSeekExtraColors.current
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
    NodeSeekTheme {
        NodeSeekErrorState(error = NodeSeekError.Cloudflare, onRetry = {}, onOpenBrowser = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Login required · dark")
@Composable
private fun SignInStatePreview() {
    NodeSeekTheme(darkTheme = true) {
        NodeSeekErrorState(
            error = NodeSeekError.LoginRequired,
            onRetry = {},
            onOpenBrowser = {},
            boardTitle = "内版",
            onBrowseElsewhere = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Network error")
@Composable
private fun NetworkStatePreview() {
    NodeSeekTheme {
        NodeSeekErrorState(error = NodeSeekError.Network, onRetry = {}, onOpenBrowser = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Empty feed")
@Composable
private fun EmptyFeedStatePreview() {
    NodeSeekTheme { EmptyFeedState(onBrowseElsewhere = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "No results")
@Composable
private fun NoResultsStatePreview() {
    NodeSeekTheme { NoSearchResultsState(onClearQuery = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Signed out")
@Composable
private fun SignedOutStatePreview() {
    NodeSeekTheme { SignedOutState(onSignIn = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Signed in")
@Composable
private fun SignedInStatePreview() {
    NodeSeekTheme { SignedInState(onSignOut = {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Deleted")
@Composable
private fun DeletedStatePreview() {
    NodeSeekTheme { DeletedContentState(onBack = {}) }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Loading")
@Composable
private fun LoadingStatePreview() {
    NodeSeekTheme {
        Box(Modifier.fillMaxWidth()) { LoadingState() }
    }
}
