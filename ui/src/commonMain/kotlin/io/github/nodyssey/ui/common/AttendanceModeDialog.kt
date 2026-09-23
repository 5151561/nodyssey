package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.assets_sign_in_choice_hint
import io.github.nodyssey.ui.resources.assets_sign_in_choice_title
import io.github.nodyssey.ui.resources.assets_sign_in_fixed_label
import io.github.nodyssey.ui.resources.assets_sign_in_fixed_value
import io.github.nodyssey.ui.resources.assets_sign_in_random_label
import io.github.nodyssey.ui.resources.assets_sign_in_random_value
import io.github.plaza.designsys.component.PlazaIcons
import org.jetbrains.compose.resources.stringResource

/**
 * The site's sign-in is a choice, not a button: gamble on a random count or take a flat five.
 *
 * Presented at tap time rather than as a setting, because it is a daily decision and the site's own
 * page asks it the same way. Shared with 我的 so that signing in there is the same two taps it is on
 * 账户与成长 — the profile entry used to push this screen just to reach this dialog.
 *
 * 9a draws the two answers as two big side-by-side tiles and nothing else: tapping one *is* signing
 * in, so there is no confirm button, and no cancel button either — a tap outside or back dismisses it,
 * as it always could. 9a's 「1~15 个」 under 随机 is not repeated: the site publishes no range, so the
 * tile says 拼手气 and the hint below keeps the site's own caveat.
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
        icon = { Icon(PlazaIcons.EventAvailable, contentDescription = null) },
        title = { Text(stringResource(Res.string.assets_sign_in_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeTile(
                        icon = PlazaIcons.Casino,
                        label = stringResource(Res.string.assets_sign_in_random_label),
                        value = stringResource(Res.string.assets_sign_in_random_value),
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        onClick = { onPick(AttendanceMode.RANDOM) },
                        modifier = Modifier.weight(1f),
                    )
                    ModeTile(
                        icon = PlazaIcons.PushPin,
                        label = stringResource(Res.string.assets_sign_in_fixed_label),
                        value = stringResource(Res.string.assets_sign_in_fixed_value),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onPick(AttendanceMode.FIXED_FIVE) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(Res.string.assets_sign_in_choice_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ModeTile(
    icon: ImageVector,
    label: String,
    value: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
