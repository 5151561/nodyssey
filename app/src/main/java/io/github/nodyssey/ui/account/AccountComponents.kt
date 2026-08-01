package io.github.nodyssey.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.ui.postlist.toNodeSeekError
import io.github.nodyssey.ui.theme.Spacing

/** The radius every field, card and sheet on these screens rounds to. */
internal val AccountFieldShape = RoundedCornerShape(14.dp)

/**
 * Something a sub-page needs to tell the user once, in a snackbar.
 */
sealed interface AccountMessage {
    data class Info(@StringRes val textRes: Int) : AccountMessage

    data class Failure(val error: NodeSeekError) : AccountMessage
}

/** Turns a thrown load/save failure into something sayable. */
fun Throwable.toAccountMessage(): AccountMessage = AccountMessage.Failure(toNodeSeekError())

@Composable
internal fun accountMessageText(message: AccountMessage): String =
    when (message) {
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
        NodeSeekError.RateLimited -> R.string.status_rate_limited_title
        is NodeSeekError.Http, NodeSeekError.Unknown -> R.string.status_unknown_title
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
