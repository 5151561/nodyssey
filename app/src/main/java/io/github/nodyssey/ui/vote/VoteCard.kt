package io.github.nodyssey.ui.vote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.VoteItem
import io.github.nodyssey.model.totalCount
import io.github.nodyssey.ui.common.shortMessage
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.richtext.VoteCardSurface
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

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
        onToggleVoters = viewModel::toggleVoters,
        onLoadMoreVoters = viewModel::loadMoreVoters,
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
 *
 * The card keeps to two faces and to as little furniture as it can. Before voting an option is a tick
 * and a label; after voting the same line grows a hairline bar and its numbers. Nothing gets a box of
 * its own — this arrives in the middle of someone's post, and a stack of filled slabs would carry more
 * weight than the paragraph it interrupts.
 */
@Composable
fun VoteCard(
    state: VoteUiState,
    onRetry: () -> Unit,
    onToggle: (Long) -> Unit,
    onSubmit: () -> Unit,
    onSetLocked: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onToggleVoters: (Long) -> Unit,
    onLoadMoreVoters: (Long) -> Unit,
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
                    onToggleVoters = onToggleVoters,
                    onLoadMoreVoters = onLoadMoreVoters,
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
    onToggleVoters: (Long) -> Unit,
    onLoadMoreVoters: (Long) -> Unit,
    onSignIn: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val total = vote.totalCount

    VoteHeader(
        state = state,
        vote = vote,
        total = total,
        onLock = { confirmLock = true },
        onUnlock = { onSetLocked(false) },
        onDelete = { confirmDelete = true },
    )

    // The option with the most votes gets the one emphasis a result row has left, so the answer to
    // the question can be read without comparing three bars by eye.
    val leading = if (state.showsResults) vote.items.mapNotNull(VoteItem::count).maxOrNull() else null

    Column(
        modifier = Modifier.padding(top = Spacing.sm),
        // Results are two-part blocks and need air between them; choice rows carry their own touch
        // target and would drift apart if the column added more on top of it.
        verticalArrangement = Arrangement.spacedBy(if (state.showsResults) Spacing.sm else 0.dp),
    ) {
        vote.items.forEach { item ->
            if (state.showsResults) {
                VoteResultRow(
                    item = item,
                    total = total,
                    leading = leading != null && leading > 0 && item.count == leading,
                    voters = state.voters[item.itemId]?.takeIf { vote.isPublic },
                    onToggleVoters = { onToggleVoters(item.itemId) }.takeIf { vote.isPublic },
                    onLoadMore = { onLoadMoreVoters(item.itemId) },
                    onUserClick = onUserClick,
                )
            } else {
                VoteChoiceRow(
                    item = item,
                    multiple = vote.multiple,
                    selected = item.itemId in state.selectedIds,
                    enabled = state.isSignedIn && !vote.locked && !state.hasVoted && !state.isSubmitting,
                    onToggle = { onToggle(item.itemId) },
                )
            }
        }
    }

    // No button once voted or locked: neither state has anything left to submit, and a disabled
    // button in a card that is otherwise finished reads as something the reader failed to do.
    if (!state.hasVoted && !vote.locked) {
        Text(
            stringResource(R.string.vote_results_hidden),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
        )
        Button(
            onClick = { if (state.isSignedIn) confirmSubmit = true else onSignIn() },
            enabled = !state.isSubmitting && (!state.isSignedIn || state.selectedIds.isNotEmpty()),
            modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth(),
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

/**
 * Title, badge and the vote's own properties.
 *
 * The total sits here rather than under the options: it describes the vote, like "单选" and "匿名投票"
 * do, and as a footer it left the card ending on a stray grey line once the button was gone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoteHeader(
    state: VoteUiState,
    vote: Vote,
    total: Int?,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    PlazaIcons.Poll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    vote.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            // Separate Text nodes rather than one joined string: each of these is a fact about the
            // vote that TalkBack should be able to land on, and "单选 · 已锁定" as one blob is also
            // one long line to re-read every time the card redraws.
            VoteMetaLine(
                listOfNotNull(
                    stringResource(
                        if (vote.multiple) R.string.vote_multiple_choice else R.string.vote_single_choice,
                    ),
                    stringResource(R.string.vote_anonymous).takeIf { !vote.isPublic },
                    stringResource(R.string.vote_locked).takeIf { vote.locked },
                    total?.let { stringResource(R.string.vote_total, it) }.takeIf { state.showsResults },
                ),
            )
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

/** The vote's own properties, as one grey line under the title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoteMetaLine(parts: List<String>) {
    val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES)
    FlowRow(modifier = Modifier.padding(top = 2.dp, start = 20.dp)) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Text(
                    " · ",
                    style = style,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(part, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * An option before the vote is cast.
 *
 * No container of its own: a box per option turned a three-option vote into three stacked slabs, and
 * the card was heavier than the paragraph it interrupts. What is left is a tick and a label, and the
 * tick is the only thing that changes when the reader picks one.
 *
 * The row carries the [selectable]/[toggleable] semantics itself, which is both why the target is the
 * full width and why the tick can be a drawing rather than a real control — a `RadioButton` inside a
 * clickable row either announces itself twice or, silenced, announces nothing at all.
 */
@Composable
private fun VoteChoiceRow(
    item: VoteItem,
    multiple: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(OptionShape)
            .then(
                if (multiple) {
                    Modifier.toggleable(
                        value = selected,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() },
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = onToggle,
                    )
                },
            )
            .defaultMinSize(minHeight = Sizes.minTouchTarget)
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteTick(selected = selected, multiple = multiple, enabled = enabled)
        Text(
            item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = Spacing.md),
        )
    }
}

/** The mark for "this one". Drawn rather than a control — see [VoteChoiceRow]. */
@Composable
private fun VoteTick(
    selected: Boolean,
    multiple: Boolean,
    enabled: Boolean,
) {
    val shape = if (multiple) RoundedCornerShape(6.dp) else CircleShape
    val tint by animateColorAsState(
        targetValue =
        when {
            selected -> MaterialTheme.colorScheme.primary
            enabled -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "voteTick",
    )
    Box(
        modifier =
        Modifier
            .size(TICK_SIZE)
            .clip(shape)
            .background(if (selected) tint else Color.Transparent)
            .border(2.dp, tint, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * An option once the results exist: one line of text, one hairline of bar under it.
 *
 * The bar is 4dp and sits tight under its own label, because three of these have to read as a
 * comparison at a glance. A taller bar — or a filled row — says the same thing while taking three
 * times the space, in a card that interrupts someone's post.
 *
 * On a public vote the whole block is the handle for the voter list, which is why there is no button
 * for it: a row of "12 人" under every option was a third of the card's height spent on an affordance
 * the count beside it already named.
 */
@Composable
private fun VoteResultRow(
    item: VoteItem,
    total: Int?,
    leading: Boolean,
    voters: VoterListState?,
    onToggleVoters: (() -> Unit)?,
    onLoadMore: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    val fraction =
        if (item.count != null && total != null && total > 0) item.count.toFloat() / total else 0f
    // Grown rather than snapped: this row is redrawn the moment the submit comes back, and a bar that
    // appears at its final length loses the only feedback the reader gets that the vote was counted.
    val grown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "voteBar",
    )
    val fill =
        if (item.voted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    val open = voters?.expanded == true
    val togglable = onToggleVoters != null && (item.count ?: 0) > 0
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(OptionShape)
            .then(
                if (togglable) {
                    Modifier.clickable(
                        onClickLabel =
                        if (open) {
                            stringResource(R.string.vote_voters_collapse)
                        } else {
                            stringResource(R.string.vote_voters_expand, item.count ?: 0)
                        },
                        onClick = { onToggleVoters?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            .padding(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.voted) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp).padding(end = 3.dp),
                )
            }
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.voted) FontWeight.SemiBold else null,
                modifier = Modifier.weight(1f),
            )
            // The one mark that says this line opens: without it the voter list — which the site has
            // and which is half of why a public vote is public — is a feature nobody would find. It
            // stays put once open, because by then it is the way back.
            if (togglable) {
                Icon(
                    PlazaIcons.Group,
                    contentDescription = null,
                    tint =
                    if (open) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(17.dp).padding(end = 3.dp),
                )
            }
            if (item.count != null) {
                Text(
                    stringResource(R.string.vote_count, item.count),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (total != null && total > 0) {
                    Text(
                        stringResource(R.string.vote_percent, (fraction * 100).toInt()),
                        style =
                        MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color =
                        if (leading) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.End,
                        // A fixed column so the percentages line up down the card instead of
                        // stepping left and right with the width of their own digits.
                        modifier = Modifier.padding(start = Spacing.sm).widthIn(min = PERCENT_COLUMN),
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(top = 3.dp)
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(grown)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(fill),
            )
        }
        if (open && voters != null) VoterStrip(list = voters, onLoadMore = onLoadMore, onUserClick = onUserClick)
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
    list: VoterListState,
    onLoadMore: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    FlowRow(modifier = Modifier.padding(top = Spacing.xs)) {
        list.uids.forEach { uid ->
            // uid as the name, because that is all the endpoint returns. Fetching a display name
            // per avatar would be one request each for a strip that is decoration.
            UserAvatar(
                url = NodeSeekSite.avatarUrl(uid),
                name = uid.toString(),
                size = VOTER_AVATAR,
                // The avatar itself is decorative (UserAvatar passes a null description), so the
                // uid has to reach TalkBack through the click label or the strip is a dead end.
                //
                // Padding *inside* the clickable, so the gap between two avatars belongs to the one
                // the finger is nearer to. Ordered the other way it would be dead space, and these
                // are the handles onto everyone who voted.
                modifier =
                Modifier
                    .clickable(
                        onClickLabel = stringResource(R.string.vote_voter_uid, uid),
                    ) { onUserClick(uid) }
                    .padding(Spacing.xs),
            )
        }
        if (list.isLoading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (list.hasMore) {
            TextButton(onClick = onLoadMore) { Text(stringResource(R.string.vote_voters_more)) }
        }
    }
}

/**
 * The card's own skeleton, in the shapes the loaded card will use.
 *
 * A bare spinner said "something is happening" without saying what, and the card then jumped from one
 * line to five. Three bars at the option rows' height reserve roughly the space the answer needs.
 */
@Composable
private fun VoteLoading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            PlazaIcons.Poll,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.vote_placeholder_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
    Column(
        modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SKELETON_WIDTHS.forEach { width ->
            SkeletonBar(fraction = width, height = 14.dp)
        }
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
            PlazaIcons.Poll,
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

/** Only ever seen as a ripple: the option rows have no fill of their own. */
private val OptionShape = RoundedCornerShape(8.dp)

private val TICK_SIZE = 20.dp

/** A hairline. Any taller and three of these read as blocks rather than as lengths to compare. */
private val BAR_HEIGHT = 4.dp

/** Fits "100%" at [androidx.compose.material3.Typography.labelMedium]. */
private val PERCENT_COLUMN = 32.dp

private val VOTER_AVATAR = 28.dp

private val SKELETON_WIDTHS = listOf(0.55f, 0.85f, 0.7f)

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
    PlazaTheme {
        VoteCard(
            state = VoteUiState(vote = previewVote(), isLoading = false, isSignedIn = true),
            onRetry = {},
            onToggle = {},
            onSubmit = {},
            onSetLocked = {},
            onDelete = {},
            onToggleVoters = {},
            onLoadMoreVoters = {},
            onSignIn = {},
            onUserClick = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "投票 · 已选中")
@Composable
private fun VoteCardSelectedPreview() {
    PlazaTheme {
        VoteCard(
            state =
            VoteUiState(
                vote = previewVote(),
                isLoading = false,
                isSignedIn = true,
                selectedIds = setOf(13202),
            ),
            onRetry = {},
            onToggle = {},
            onSubmit = {},
            onSetLocked = {},
            onDelete = {},
            onToggleVoters = {},
            onLoadMoreVoters = {},
            onSignIn = {},
            onUserClick = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "投票 · 已投票")
@Composable
private fun VoteCardVotedPreview() {
    PlazaTheme {
        VoteCard(
            state = VoteUiState(vote = previewVote(voted = true), isLoading = false, isSignedIn = true),
            onRetry = {},
            onToggle = {},
            onSubmit = {},
            onSetLocked = {},
            onDelete = {},
            onToggleVoters = {},
            onLoadMoreVoters = {},
            onSignIn = {},
            onUserClick = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "投票 · 载入中")
@Composable
private fun VoteCardLoadingPreview() {
    PlazaTheme {
        VoteCard(
            state = VoteUiState(isLoading = true),
            onRetry = {},
            onToggle = {},
            onSubmit = {},
            onSetLocked = {},
            onDelete = {},
            onToggleVoters = {},
            onLoadMoreVoters = {},
            onSignIn = {},
            onUserClick = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
