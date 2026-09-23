package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.plaza.designsys.component.TonalTile
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
                    val scheme = MaterialTheme.colorScheme
                    listOf(
                        Triple(PlazaIcons.Casino, AttendanceMode.RANDOM, scheme.primary),
                        Triple(PlazaIcons.PushPin, AttendanceMode.FIXED_FIVE, scheme.secondaryContainer),
                    ).forEach { (icon, mode, container) ->
                        val random = mode == AttendanceMode.RANDOM
                        TonalTile(
                            onClick = { onPick(mode) },
                            containerColor = container,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(
                                stringResource(
                                    if (random) Res.string.assets_sign_in_random_label else Res.string.assets_sign_in_fixed_label,
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                stringResource(
                                    if (random) Res.string.assets_sign_in_random_value else Res.string.assets_sign_in_fixed_value,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
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
