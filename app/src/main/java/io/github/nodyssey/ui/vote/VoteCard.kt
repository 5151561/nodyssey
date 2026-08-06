package io.github.nodyssey.ui.vote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.VoteItem
import io.github.nodyssey.model.totalCount
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.richtext.VoteCardSurface
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing

@Composable
fun VoteCard(
    viewModel: VoteViewModel,
    onSignIn: () -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VoteCard(
        state = state,
        onRetry = viewModel::load,
        onToggle = viewModel::toggleSelection,
        onSubmit = viewModel::submit,
        onSetLocked = viewModel::setLocked,
        onDelete = viewModel::delete,
        onExpandVoters = viewModel::expandVoters,
        onSignIn = onSignIn,
        onUserClick = onUserClick,
        modifier = modifier,
    )
}

/**
 * A vote, in whichever of its states the server has put it.
 *
 * The one rule that shapes everything below: **results do not exist until this account has voted.**
 * NodeSeek omits the counts entirely from an unvoted read, so an unvoted card shows options and
 * nothing else — no bars, no percentages, no totals, and no voter avatars either, even though the
 * voter endpoint would in fact answer. Matching the site here is deliberate; showing a tally it
 * chose to withhold would change what participating means.
 */
@Composable
fun VoteCard(
    state: VoteUiState,
    onRetry: () -> Unit,
    onToggle: (Long) -> Unit,
    onSubmit: () -> Unit,
    onSetLocked: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExpandVoters: (Long) -> Unit,
    onSignIn: () -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vote = state.vote

    VoteCardSurface(modifier = modifier) {
        when {
            state.deleted -> Text(stringResource(R.string.vote_deleted), style = MaterialTheme.typography.bodyMedium)

            vote == null && state.isLoading -> VoteLoading()

            vote == null ->
                VoteLoadFailed(message = state.error?.shortMessage(), onRetry = onRetry)

            else ->
                VoteBody(
                    state = state,
                    vote = vote,
                    onToggle = onToggle,
                    onSubmit = onSubmit,
                    onSetLocked = onSetLocked,
                    onDelete = onDelete,
                    onExpandVoters = onExpandVoters,
                    onSignIn = onSignIn,
                    onUserClick = onUserClick,
                )
        }
    }
}

@Composable
private fun VoteBody(
    state: VoteUiState,
    vote: Vote,
    onToggle: (Long) -> Unit,
    onSubmit: () -> Unit,
    onSetLocked: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExpandVoters: (Long) -> Unit,
    onSignIn: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    VoteHeader(
        state = state,
        vote = vote,
        onLock = { confirmLock = true },
        onUnlock = { onSetLocked(false) },
        onDelete = { confirmDelete = true },
    )

    val total = vote.totalCount
    vote.items.forEach { item ->
        VoteOptionRow(
            item = item,
            multiple = vote.multiple,
            selected = item.itemId in state.selectedIds,
            enabled = state.isSignedIn && !vote.locked && !state.hasVoted && !state.isSubmitting,
            showsResults = state.showsResults,
            total = total,
            onToggle = { onToggle(item.itemId) },
        )
        if (state.showsResults && vote.isPublic) {
            VoterStrip(
                item = item,
                list = state.voters[item.itemId],
                onExpand = { onExpandVoters(item.itemId) },
                onUserClick = onUserClick,
            )
        }
    }

    if (state.showsResults && total != null) {
        Text(
            stringResource(R.string.vote_total, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    } else if (!state.hasVoted && !vote.locked) {
        Text(
            stringResource(R.string.vote_results_hidden),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }

    // No button once voted or locked: neither state has anything left to submit, and a disabled
    // button in a card that is otherwise finished reads as something the reader failed to do.
    if (!state.hasVoted && !vote.locked) {
        Button(
            onClick = { if (state.isSignedIn) confirmSubmit = true else onSignIn() },
            enabled = !state.isSubmitting && (!state.isSignedIn || state.selectedIds.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(
                        if (state.isSignedIn) R.string.vote_submit else R.string.vote_sign_in_first,
                    ),
                )
            }
        }
    }

    if (confirmSubmit) {
        VoteConfirmDialog(
            title = R.string.vote_confirm_title,
            body = R.string.vote_confirm_body,
            confirm = R.string.vote_confirm_ok,
            onDismiss = { confirmSubmit = false },
            onConfirm = {
                confirmSubmit = false
                onSubmit()
            },
        )
    }
    if (confirmLock) {
        VoteConfirmDialog(
            title = R.string.vote_lock_confirm_title,
            body = R.string.vote_lock_confirm_body,
            confirm = R.string.vote_lock,
            onDismiss = { confirmLock = false },
            onConfirm = {
                confirmLock = false
                onSetLocked(true)
            },
        )
    }
    if (confirmDelete) {
        VoteConfirmDialog(
            title = R.string.vote_delete_confirm_title,
            body = R.string.vote_delete_confirm_body,
            confirm = R.string.vote_delete,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun VoteHeader(
    state: VoteUiState,
    vote: Vote,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(vote.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoteChip(
                    stringResource(
                        if (vote.multiple) R.string.vote_multiple_choice else R.string.vote_single_choice,
                    ),
                )
                if (!vote.isPublic) VoteChip(stringResource(R.string.vote_anonymous))
                if (vote.locked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    VoteChip(stringResource(R.string.vote_locked))
                }
            }
        }
        // Only when there is at least one thing this account may actually do: the owner of a locked
        // vote can neither unlock nor delete it, so an empty menu would be a promise we cannot keep.
        if (state.canLock || state.canUnlock || state.canDelete) {
            VoteManageMenu(
                state = state,
                onLock = onLock,
                onUnlock = onUnlock,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun VoteManageMenu(
    state: VoteUiState,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !state.manageInFlight) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.vote_manage))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.canLock) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vote_lock)) },
                    onClick = {
                        open = false
                        onLock()
                    },
                )
            }
            if (state.canUnlock) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vote_unlock)) },
                    onClick = {
                        open = false
                        onUnlock()
                    },
                )
            }
            if (state.canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vote_delete)) },
                    onClick = {
                        open = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun VoteOptionRow(
    item: VoteItem,
    multiple: Boolean,
    selected: Boolean,
    enabled: Boolean,
    showsResults: Boolean,
    total: Int?,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                // Once voted, the control is a read-out rather than an input: a disabled radio still
                // looks like something to press, and a tick does not.
                showsResults ->
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint =
                        if (item.voted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )

                multiple ->
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggle() },
                        enabled = enabled,
                        modifier = Modifier.clearAndSetSemantics { },
                    )

                else ->
                    RadioButton(
                        selected = selected,
                        onClick = onToggle,
                        enabled = enabled,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
            }
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
            )
            if (showsResults && item.count != null) {
                Text(
                    stringResource(R.string.vote_count, item.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showsResults && item.count != null && total != null && total > 0) {
            val fraction = item.count.toFloat() / total
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    stringResource(R.string.vote_percent, (fraction * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

/**
 * Who picked this option, on a public vote whose results this account can see.
 *
 * The first ten arrive with the vote itself, so opening the strip costs nothing; only "load more"
 * is a request. Names are never fetched — the endpoint answers with bare uids, and asking for a
 * name each would be one request per avatar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoterStrip(
    item: VoteItem,
    list: VoterListState?,
    onExpand: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    val count = item.count ?: return
    if (count == 0) return

    if (list == null) {
        TextButton(onClick = onExpand, modifier = Modifier.padding(start = Spacing.xl)) {
            Text(stringResource(R.string.vote_voters_expand, count))
        }
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(start = Spacing.xl, bottom = Spacing.xs),
    ) {
        list.uids.forEach { uid ->
            // uid as the name, because that is all the endpoint returns. Fetching a display name
            // per avatar would be one request each for a strip that is decoration.
            UserAvatar(
                url = NodeSeekSite.avatarUrl(uid),
                name = uid.toString(),
                size = 24.dp,
                // The avatar itself is decorative (UserAvatar passes a null description), so the
                // uid has to reach TalkBack through the click label or the strip is a dead end.
                modifier =
                Modifier.clickable(
                    onClickLabel = stringResource(R.string.vote_voter_uid, uid),
                ) { onUserClick(uid) },
            )
        }
        if (list.isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (list.hasMore) {
            TextButton(onClick = onExpand) { Text(stringResource(R.string.vote_voters_more)) }
        }
    }
}

@Composable
private fun VoteChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 1.dp),
        )
    }
}

@Composable
private fun VoteLoading() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.vote_placeholder_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A row, not a full-screen status view: this sits in the middle of an article the reader is still
 * reading, and taking the thread over for one failed embed would be out of proportion.
 */
@Composable
private fun VoteLoadFailed(
    message: String?,
    onRetry: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            NodysseyIcons.Poll,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            message ?: stringResource(R.string.vote_load_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.vote_retry)) }
    }
}

@Composable
private fun VoteConfirmDialog(
    title: Int,
    body: Int,
    confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun previewVote(
    locked: Boolean = false,
    voted: Boolean = false,
) = Vote(
    id = 2871,
    title = "哪个运营商比较好",
    ownerUid = 57815,
    isPublic = true,
    locked = locked,
    multiple = false,
    items =
    listOf(
        VoteItem(13201, "移动", voted = voted, count = if (voted) 12 else null),
        VoteItem(13202, "联通", voted = false, count = if (voted) 5 else null),
        VoteItem(13203, "电信", voted = false, count = if (voted) 23 else null),
    ),
)

@Preview(showBackground = true, widthDp = 360, name = "投票 · 未投票")
@Composable
private fun VoteCardUnvotedPreview() {
    NodysseyTheme {
        VoteCard(
            state = VoteUiState(vote = previewVote(), isLoading = false, isSignedIn = true),
            onRetry = {},
            onToggle = {},
            onSubmit = {},
            onSetLocked = {},
            onDelete = {},
            onExpandVoters = {},
            onSignIn = {},
            onUserClick = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "投票 · 已投票")
@Composable
private fun VoteCardVotedPreview() {
    NodysseyTheme {
        VoteCard(
            state = VoteUiState(vote = previewVote(voted = true), isLoading = false, isSignedIn = true),
            onRetry = {},
            onToggle = {},
            onSubmit = {},
            onSetLocked = {},
            onDelete = {},
            onExpandVoters = {},
            onSignIn = {},
            onUserClick = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
