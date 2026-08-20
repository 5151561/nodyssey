package io.github.nodyssey.ui.stardust

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.core.StardustReceiveMarkup
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.stardust_compose_amount
import io.github.nodyssey.ui.resources.stardust_compose_amount_hint
import io.github.nodyssey.ui.resources.stardust_compose_insert
import io.github.nodyssey.ui.resources.stardust_compose_needs_sign_in
import io.github.nodyssey.ui.resources.stardust_compose_note
import io.github.nodyssey.ui.resources.stardust_compose_note_hint
import io.github.nodyssey.ui.resources.stardust_compose_onetime
import io.github.nodyssey.ui.resources.stardust_compose_onetime_body
import io.github.nodyssey.ui.resources.stardust_compose_ref
import io.github.nodyssey.ui.resources.stardust_compose_ref_hint
import io.github.nodyssey.ui.resources.stardust_compose_ref_support
import io.github.nodyssey.ui.resources.stardust_compose_title
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * 插入星辰收款码 — the composer's side of a receive code.
 *
 * The opposite of `VoteComposeDialog` in what it has to do: a vote must exist server-side before
 * there is an id to reference, while a receive code has no server object at all. The marker *is* the
 * code, and pressing 插入 writes text and nothing else. Nothing has been asked of the site until
 * somebody pays.
 *
 * The four fields are the site's own generator, in its order. 数额 and Ref ID are held to positive
 * integers because the site's own reader is: a marker whose numbers are not bare digits is abandoned
 * and shown as a raw link, on the web and here — see `StardustReceiveMarkup.parse`.
 *
 * [needsSignIn] is for the case where the app cannot name the payee: the code collects for whoever
 * inserts it, and without a uid there is nothing to write.
 */
@Composable
fun StardustReceiveComposeDialog(
    onInsert: (amount: Int, refId: Long, description: String, onetime: Boolean) -> Unit,
    onDismiss: () -> Unit,
    needsSignIn: Boolean = false,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    // Seeded the way the site's generator seeds them, and editable for the same reason: the Ref ID is
    // the payee's own reference, so a number nobody had to think about is the right starting point —
    // and it is the number the tally is keyed on, which an empty box full of `1`s would collide.
    var refId by rememberSaveable { mutableStateOf(StardustReceiveMarkup.randomRefId().toString()) }
    var description by rememberSaveable { mutableStateOf(StardustReceiveMarkup.DEFAULT_DESCRIPTION) }
    var onetime by rememberSaveable { mutableStateOf(false) }

    val parsedAmount = amount.toPositiveIntOrNull()
    val parsedRefId = refId.toPositiveLongOrNull()
    val canInsert = !needsSignIn && parsedAmount != null && parsedRefId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(PlazaIcons.QrCode, contentDescription = null) },
        title = { Text(stringResource(Res.string.stardust_compose_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text(stringResource(Res.string.stardust_compose_amount)) },
                    placeholder = { Text(stringResource(Res.string.stardust_compose_amount_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = refId,
                    onValueChange = { refId = it.filter(Char::isDigit) },
                    label = { Text(stringResource(Res.string.stardust_compose_ref)) },
                    placeholder = { Text(stringResource(Res.string.stardust_compose_ref_hint)) },
                    supportingText = { Text(stringResource(Res.string.stardust_compose_ref_support)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.stardust_compose_note)) },
                    placeholder = { Text(stringResource(Res.string.stardust_compose_note_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    OnetimeRow(checked = onetime, onChange = { onetime = it })
                }

                if (needsSignIn) {
                    Text(
                        stringResource(Res.string.stardust_compose_needs_sign_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Both are non-null whenever the button is enabled; the check is here so the
                    // compiler does not have to take that on trust.
                    val value = parsedAmount
                    val ref = parsedRefId
                    if (value != null && ref != null) onInsert(value, ref, description.trim(), onetime)
                },
                enabled = canInsert,
            ) {
                Text(stringResource(Res.string.stardust_compose_insert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun OnetimeRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .defaultMinSize(minHeight = Sizes.minTouchTarget)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(stringResource(Res.string.stardust_compose_onetime), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.stardust_compose_onetime_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // null, not `onChange`: the row already carries the toggle and its semantics.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Zero is not a payment, and the site's own reader would take `0` but the code would be pointless. */
private fun String.toPositiveIntOrNull(): Int? = toIntOrNull()?.takeIf { it > 0 }

private fun String.toPositiveLongOrNull(): Long? = toLongOrNull()?.takeIf { it > 0 }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StardustReceiveComposeDialogPreview() {
    PlazaTheme {
        StardustReceiveComposeDialog(onInsert = { _, _, _, _ -> }, onDismiss = {})
    }
}
