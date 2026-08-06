package io.github.nodyssey.ui.vote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing

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
        title = { Text(stringResource(R.string.vote_compose_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.vote_compose_question)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { options[index] = it },
                            label = { Text(stringResource(R.string.vote_compose_option, index + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        // Two is the floor: a vote with one option is not a question.
                        IconButton(
                            onClick = { options.removeAt(index) },
                            enabled = options.size > MIN_OPTIONS,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.vote_compose_remove_option, index + 1),
                            )
                        }
                    }
                }

                TextButton(onClick = { options.add("") }, enabled = options.size < MAX_OPTIONS) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.vote_compose_add_option),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }

                VoteToggleRow(
                    label = stringResource(R.string.vote_compose_multiple),
                    checked = multiple,
                    onChange = { multiple = it },
                )
                VoteToggleRow(
                    label = stringResource(R.string.vote_compose_public),
                    description = stringResource(R.string.vote_compose_public_body),
                    checked = isPublic,
                    onChange = { isPublic = it },
                )

                (state as? VoteCreationState.Failed)?.let { failure ->
                    Text(
                        failure.detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.vote_compose_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirming = true }, enabled = canCreate) {
                if (state is VoteCreationState.InFlight) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.vote_compose_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = state !is VoteCreationState.InFlight) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.vote_compose_confirm_title)) },
            text = { Text(stringResource(R.string.vote_compose_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onCreate(title.trim(), multiple, isPublic, filled)
                    },
                ) { Text(stringResource(R.string.vote_compose_create)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Two is the minimum that makes a question; the site's own editor starts at two as well. */
private const val MIN_OPTIONS = 2

/** Not a site limit we have measured — a guard against a dialog that scrolls forever. */
private const val MAX_OPTIONS = 20

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VoteComposeDialogPreview() {
    NodysseyTheme {
        VoteComposeDialog(state = VoteCreationState.Idle, onCreate = { _, _, _, _ -> }, onDismiss = {})
    }
}
