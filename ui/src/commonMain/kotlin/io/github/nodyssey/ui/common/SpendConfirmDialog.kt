package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.spend_got_it
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/**
 * One line of the "what exactly is being spent" block.
 *
 * [separated] draws a hairline above the line — the rule 8d and 9e put between what is being spent
 * and what it leaves behind. [isError] paints the whole line in the error tone, for the one line that
 * is the reason the spend cannot happen (9e's 还差).
 */
data class SpendDetail(
    val label: String,
    val value: String,
    val note: String? = null,
    val separated: Boolean = false,
    val isError: Boolean = false,
)

/**
 * The single confirmation layer in front of every irreversible spend: transfer, invite code, feeding.
 *
 * One component rather than three dialogs because the thing being confirmed is always the same shape —
 * how much leaves the account, where it goes, what is left afterwards, and that none of it can be
 * undone. Reading those four facts in the same place each time is what makes a habitual tap safe.
 * They sit on one white card on the dialog's tinted surface (8d, 9e); [header] is an optional card
 * above them for *who* — 8d's recipient, avatar and name, which is the fact a transfer turns on.
 *
 * [shortfall] is what turns it into a dead end on purpose (9e): when the balance cannot cover the
 * amount, there is no confirm at all — the layer names the gap and offers one button, 知道了, which
 * closes it. A disabled 确认 beside a live 取消 said the same thing less plainly, and "确认" that fails
 * server-side teaches the user nothing.
 *
 * [isSending] seals the layer while the request is in flight — no second tap, no cancel, no dismiss by
 * back or by tapping outside. None of those can call the spend back once it has left, and a layer that
 * vanishes mid-flight would leave the user with no idea whether it went.
 */
@Composable
fun SpendConfirmDialog(
    title: String,
    details: List<SpendDetail>,
    caution: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Warning,
    shortfall: String? = null,
    isSending: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val card = LocalPlazaLayers.current.card
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        modifier = modifier,
        icon = icon?.let { { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                header?.let {
                    Surface(color = card, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) { it() }
                }
                Surface(color = card, shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        details.forEach { detail -> DetailLine(detail) }
                    }
                }
                Text(
                    text = caution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Said once: a caller that already worked the gap out on the card, in the error tone,
                // does not need it repeated underneath.
                shortfall?.takeIf { details.none(SpendDetail::isError) }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (shortfall != null) {
                Button(onClick = onDismiss) {
                    Text(stringResource(Res.string.spend_got_it))
                }
            } else {
                Button(onClick = onConfirm, enabled = !isSending) {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton =
        if (shortfall != null) {
            null
        } else {
            {
                TextButton(onClick = onDismiss, enabled = !isSending) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun DetailLine(detail: SpendDetail) {
    if (detail.separated) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    val tint = if (detail.isError) MaterialTheme.colorScheme.error else null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = detail.label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail.value,
            style =
            MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
        detail.note?.let {
            Text(
                text = " · $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SpendConfirmDialogPreview() {
    PlazaTheme {
        SpendConfirmDialog(
            title = "确认转账 2 星辰？",
            details =
            listOf(
                SpendDetail("数额", "2 星辰"),
                SpendDetail("收款人 UID", "28742", note = "demain"),
                SpendDetail("Ref ID", "866042"),
                SpendDetail("转账后余额", "4 → 2"),
            ),
            caution = "星辰转账一旦提交无法撤销，请确认收款人 UID 与 Ref ID 无误。",
            confirmLabel = "确认转账",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "余额不足")
@Composable
private fun SpendConfirmDialogShortfallPreview() {
    PlazaTheme(darkTheme = true) {
        SpendConfirmDialog(
            title = "确认转账 7 星辰？",
            details = listOf(SpendDetail("数额", "7 星辰"), SpendDetail("当前余额", "4")),
            caution = "星辰转账一旦提交无法撤销。",
            confirmLabel = "确认转账",
            onConfirm = {},
            onDismiss = {},
            shortfall = "还差 3 星辰",
        )
    }
}
