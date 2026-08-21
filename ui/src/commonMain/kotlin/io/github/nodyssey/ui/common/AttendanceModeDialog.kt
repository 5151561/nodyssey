package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.assets_sign_in_choice_hint
import io.github.nodyssey.ui.resources.assets_sign_in_choice_title
import io.github.nodyssey.ui.resources.assets_sign_in_fixed
import io.github.nodyssey.ui.resources.assets_sign_in_random
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The site's sign-in is a choice, not a button: gamble on a random count or take a flat five.
 *
 * Presented at tap time rather than as a setting, because it is a daily decision and the site's own
 * page asks it the same way. Shared with 我的 so that signing in there is the same two taps it is on
 * 账户与成长 — the profile entry used to push this screen just to reach this dialog.
 */
@Composable
fun AttendanceModeDialog(
    onPick: (AttendanceMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(NodeSeekIcons.ChickenLeg, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.assets_sign_in_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { onPick(AttendanceMode.RANDOM) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(Res.string.assets_sign_in_random))
                }
                FilledTonalButton(
                    onClick = { onPick(AttendanceMode.FIXED_FIVE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(Res.string.assets_sign_in_fixed))
                }
                Text(
                    text = stringResource(Res.string.assets_sign_in_choice_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
