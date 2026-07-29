package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

/** One line of the "what exactly is being spent" block. */
data class SpendDetail(
    val label: String,
    val value: String,
    val note: String? = null,
)

/**
 * The single confirmation layer in front of every irreversible spend: transfer, invite code, feeding.
 *
 * One component rather than three dialogs because the thing being confirmed is always the same shape —
 * how much leaves the account, where it goes, what is left afterwards, and that none of it can be
 * undone. Reading those four facts in the same place each time is what makes a habitual tap safe.
 *
 * [shortfall] is what turns it into a dead end on purpose: when the balance cannot cover the amount,
 * the confirm button is disabled and the gap is named, because "确认" that fails server-side teaches
 * the user nothing.
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
    icon: ImageVector = Icons.Default.Warning,
    shortfall: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        details.forEach { detail ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = detail.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = detail.value,
                                    style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFeatureSettings = TABULAR_FIGURES,
                                    ),
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
                    }
                }
                Text(
                    text = caution,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                shortfall?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = shortfall == null) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SpendConfirmDialogPreview() {
    NodysseyTheme {
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
    NodysseyTheme(darkTheme = true) {
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
