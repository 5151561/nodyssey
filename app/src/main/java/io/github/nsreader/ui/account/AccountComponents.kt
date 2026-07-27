package io.github.nsreader.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.account.EndpointNotVerifiedException
import io.github.nsreader.ui.postlist.toNodeSeekError
import io.github.nsreader.ui.theme.Spacing

/** The radius every field, card and sheet on these screens rounds to. */
internal val AccountFieldShape = RoundedCornerShape(14.dp)

/**
 * Something a sub-page needs to tell the user once, in a snackbar.
 *
 * [EndpointPending] carries a string resource rather than the repository's operation name: that name
 * is a Kotlin identifier meant for whoever fills the endpoint in, and putting `saveProfileFields` in
 * front of a reader is worse than saying nothing.
 */
sealed interface AccountMessage {
    data class EndpointPending(@StringRes val labelRes: Int) : AccountMessage

    data class Info(@StringRes val textRes: Int) : AccountMessage

    data class Failure(val error: NodeSeekError) : AccountMessage
}

/** Turns a thrown load/save failure into either the pending seam or a real network error. */
fun Throwable.toAccountMessage(@StringRes labelRes: Int): AccountMessage =
    if (this is EndpointNotVerifiedException) {
        AccountMessage.EndpointPending(labelRes)
    } else {
        AccountMessage.Failure(toNodeSeekError())
    }

@Composable
internal fun accountMessageText(message: AccountMessage): String =
    when (message) {
        is AccountMessage.EndpointPending ->
            stringResource(R.string.account_endpoint_pending_toast, stringResource(message.labelRes))

        is AccountMessage.Info -> stringResource(message.textRes)

        is AccountMessage.Failure -> stringResource(message.error.messageRes())
    }

@Composable
private fun NodeSeekError.messageRes(): Int =
    when (this) {
        NodeSeekError.Cloudflare -> R.string.status_challenge_title
        NodeSeekError.LoginRequired -> R.string.status_sign_in_title
        NodeSeekError.Network -> R.string.status_network_title
        NodeSeekError.Unparsable -> R.string.status_unparsable_title
        NodeSeekError.NotWired -> R.string.status_not_wired_title
        is NodeSeekError.Http, NodeSeekError.Unknown -> R.string.status_unknown_title
    }

/**
 * Says out loud that a group's site plumbing is not connected yet.
 *
 * A banner rather than an error state, because the screen is not broken — the layout, validation and
 * confirmations all work and are worth showing. What does not work is the one request at the end, and
 * the honest thing is to say so before the user types a new password rather than after.
 */
@Composable
internal fun EndpointPendingBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.account_endpoint_pending_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    stringResource(R.string.account_endpoint_pending_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The Local / Remote storage badge d6 puts beside every preference row.
 *
 * The site itself splits its settings by where they live — this browser or this account — and the
 * app keeps the distinction visible because it changes what the user should expect: a Local row is
 * instant and this-device-only, a Remote row follows the account. Monospace, like the design, so it
 * reads as a technical annotation rather than part of the row's title.
 */
@Composable
internal fun StorageBadge(
    local: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color =
        if (local) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor =
        if (local) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        border =
        if (local) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        modifier = modifier,
    ) {
        Text(
            text = if (local) "Local" else "Remote",
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** The group heading inside a sub-page — same weight and colour as 8g's, without the list around it. */
@Composable
internal fun AccountSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.xs),
    )
}

/** Helper text under a field — the site's own wording, where it has any. */
@Composable
internal fun AccountFieldHelper(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.xs),
    )
}

/**
 * The second confirmation every high-risk action on these screens goes through.
 *
 * 8g deliberately gives none of these a switch: a mis-tapped toggle that rebinds two-factor auth or
 * changes a password is unrecoverable from inside the app, so each one is a page you have to reach and
 * then a dialog you have to read. [destructive] tints the confirm action for the ones that delete.
 */
@Composable
internal fun HighRiskDialog(
    icon: ImageVector,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // AlertDialog centres both icon and title on its own once an icon is present, which is the
        // layout the design shows; there is nothing to override here.
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color =
                    if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/**
 * A row of trailing actions pinned to the bottom of a sub-page, above the navigation bar.
 *
 * The navigation-bar inset is applied here rather than left to the caller. `Scaffold` dispatches no
 * insets to its `bottomBar` slot — `NavigationBar` and `BottomAppBar` only look like it does because
 * they carry their own `windowInsets` defaults — so an edge-to-edge app puts a bare `Surface` under
 * the gesture pill, and under the whole bar on a three-button device. Owning it in the component
 * means every caller is correct by construction.
 */
@Composable
internal fun AccountBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            content()
        }
    }
}
