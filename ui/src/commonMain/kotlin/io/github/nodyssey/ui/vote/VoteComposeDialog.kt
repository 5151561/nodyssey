package io.github.nodyssey.ui.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.vote_compose_add_option
import io.github.nodyssey.ui.resources.vote_compose_confirm_body
import io.github.nodyssey.ui.resources.vote_compose_confirm_title
import io.github.nodyssey.ui.resources.vote_compose_create
import io.github.nodyssey.ui.resources.vote_compose_failed
import io.github.nodyssey.ui.resources.vote_compose_multiple
import io.github.nodyssey.ui.resources.vote_compose_option
import io.github.nodyssey.ui.resources.vote_compose_public
import io.github.nodyssey.ui.resources.vote_compose_public_body
import io.github.nodyssey.ui.resources.vote_compose_question
import io.github.nodyssey.ui.resources.vote_compose_remove_option
import io.github.nodyssey.ui.resources.vote_compose_title
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/** How a vote creation is going, so the dialog can wait and report without owning the request. */
sealed interface VoteCreationState {
    data object Idle : VoteCreationState

    data object InFlight : VoteCreationState

    /** [detail] is the site's own sentence when it sent one. */
    data class Failed(
        val detail: String?,
    ) : VoteCreationState
}

/**
 * 插入投票 — the composer's side of a vote.
 *
 * Creating and inserting are one action from the author's point of view but two from the site's: the
 * vote is created server-side first, and only then does its id go into the body as
 * `nsapp://vote?id=N`. That ordering is why this cannot be a pure text insertion.
 *
 * The confirmation repeats the site's own warning. A vote's title and options cannot be edited after
 * creation, and a typo means a dead vote left in the post.
 */
@Composable
fun VoteComposeDialog(
    state: VoteCreationState,
    onCreate: (title: String, multiple: Boolean, isPublic: Boolean, items: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var multiple by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(true) }
    val options = remember { mutableStateListOf("", "") }
    var confirming by remember { mutableStateOf(false) }

    val filled = options.map(String::trim).filter(String::isNotEmpty)
    val canCreate = title.isNotBlank() && filled.size >= MIN_OPTIONS && state !is VoteCreationState.InFlight

    AlertDialog(
        onDismissRequest = { if (state !is VoteCreationState.InFlight) onDismiss() },
        icon = { Icon(PlazaIcons.Poll, contentDescription = null) },
        title = { Text(stringResource(Res.string.vote_compose_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.vote_compose_question)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { options[index] = it },
                            label = { Text(stringResource(Res.string.vote_compose_option, index + 1)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f),
                        )
                        // Two is the floor: a vote with one option is not a question. Below it the
                        // button is absent rather than greyed — a disabled cross on every row of a
                        // two-option vote reads as something the author has done wrong.
                        if (options.size > MIN_OPTIONS) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription =
                                    stringResource(Res.string.vote_compose_remove_option, index + 1),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { options.add("") }, enabled = options.size < MAX_OPTIONS) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(Res.string.vote_compose_add_option),
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${options.size} / $MAX_OPTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                }

                // The two switches are settings on the vote rather than more of the form, so they sit
                // in their own block instead of continuing the column of text fields.
                Column(
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    VoteToggleRow(
                        label = stringResource(Res.string.vote_compose_multiple),
                        checked = multiple,
                        onChange = { multiple = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    VoteToggleRow(
                        label = stringResource(Res.string.vote_compose_public),
                        description = stringResource(Res.string.vote_compose_public_body),
                        checked = isPublic,
                        onChange = { isPublic = it },
                    )
                }

                (state as? VoteCreationState.Failed)?.let { failure ->
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            PlazaIcons.ErrorCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            failure.detail?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.vote_compose_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirming = true }, enabled = canCreate) {
                if (state is VoteCreationState.InFlight) {
                    CircularProgressIndicator(Modifier.size(18.dp).describedAsLoading(), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.vote_compose_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = state !is VoteCreationState.InFlight) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.vote_compose_confirm_title)) },
            text = { Text(stringResource(Res.string.vote_compose_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onCreate(title.trim(), multiple, isPublic, filled)
                    },
                ) { Text(stringResource(Res.string.vote_compose_create)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun VoteToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
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
            Text(label, style = MaterialTheme.typography.bodyMedium)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // null, not `onChange`: the row above already carries the toggle and its semantics, and a
        // switch that also handles clicks announces the same control twice.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Two is the minimum that makes a question; the site's own editor starts at two as well. */
private const val MIN_OPTIONS = 2

/** Not a site limit we have measured — a guard against a dialog that scrolls forever. */
private const val MAX_OPTIONS = 20

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VoteComposeDialogPreview() {
    PlazaTheme {
        VoteComposeDialog(state = VoteCreationState.Idle, onCreate = { _, _, _, _ -> }, onDismiss = {})
    }
}
