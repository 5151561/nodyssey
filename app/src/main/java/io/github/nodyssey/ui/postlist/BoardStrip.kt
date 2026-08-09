package io.github.nodyssey.ui.postlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.nodyssey.R
import io.github.nodyssey.data.Board
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Fifteen boards do not fit on a 360dp strip, and a bottom sheet was the other candidate.
 *
 * This is the inline version: the strip *is* the picker. Collapsed it scrolls sideways through the
 * same pills; expanded it wraps them onto as many rows as they need, in place. One list drawn once —
 * an earlier version dropped a second panel underneath and simply repeated the strip's contents.
 *
 * It costs vertical space while open, but the finger never leaves the top of the screen and the list
 * underneath stays visible — which a sheet cannot claim.
 *
 * Expanded, a long press turns the same pills into an editor: drag to reorder, and tap a pill's
 * corner badge to park it at the tail or bring it back. Parked boards are only drawn while editing —
 * out of the way is the whole point of parking one — so the editor is also the only place they can be
 * recovered, which is why the long press is on the strip rather than buried in 设置.
 *
 * [parkedBoards] is deliberately a second list rather than a flag inside [boards]: outside edit mode
 * a parked board is not selectable, and [boards] is exactly the list the feed may page through.
 */
@Composable
internal fun BoardStrip(
    boards: List<Board>,
    parkedBoards: List<Board>,
    selectedSlug: String?,
    onBoardClick: (String?) -> Unit,
    onArrangementChange: (order: List<String>, parked: Set<String>) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val rowState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    /*
     * The editor works on its own copy of the strip.
     *
     * Every edit is written through immediately — a drag that survives to the finger lifting is a
     * decision, and losing it because the app was backgrounded would be indefensible — but the draft
     * stays authoritative until the editor closes. Following the store instead would mean the write
     * we just made races back through the flow and replaces the list mid-gesture.
     *
     * Keyed on `editing` alone for the same reason: a board list refresh landing while a finger is
     * down must not reshuffle what is under it.
     */
    var draft by remember { mutableStateOf(emptyList<BoardSlot>()) }
    LaunchedEffect(editing) {
        if (editing) {
            draft =
                boards.map { BoardSlot(it, parked = false) } +
                parkedBoards.map { BoardSlot(it, parked = true) }
        }
    }

    fun commit(slots: List<BoardSlot>) {
        onArrangementChange(
            slots.mapNotNull { it.board.slug },
            slots.filter { it.parked }.mapNotNull { it.board.slug }.toSet(),
        )
    }

    fun togglePark(key: String) {
        val index = draft.indexOfFirst { it.key == key }
        if (index < 0) return
        val slot = draft[index]
        if (slot.locked) return
        val next = draft.toMutableList()
        next.removeAt(index)
        if (slot.parked) {
            // Back to the end of the live half, not to wherever it used to sit: the strip has moved
            // on, and dropping it into a slot the user has since given to something else is worse
            // than an obvious "it came back at the end".
            val boundary = next.indexOfFirst { it.parked }.takeIf { it >= 0 } ?: next.size
            next.add(boundary, slot.copy(parked = false))
        } else {
            next.add(slot.copy(parked = true))
        }
        draft = next
        commit(next)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Collapsing back to one row must not hide the board the user just picked behind the fold.
    val selectedIndex = boards.indexOfFirst { it.slug == selectedSlug }
    LaunchedEffect(expanded, selectedIndex) {
        if (!expanded && selectedIndex > 0) rowState.animateScrollToItem(selectedIndex)
    }

    // Back is what every other transient mode on this screen answers to, and the editor is one.
    BackHandler(enabled = editing) { editing = false }

    Column(
        Modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ),
    ) {
        /*
         * A box with the toggle laid over the corner, not a row with the toggle beside the pills.
         *
         * A row makes the toggle's width a column that every wrapped row of pills has to keep clear,
         * so an expanded strip left a tall empty gutter down its right edge with a single button at
         * the top of it. Only the *first* row shares its line with the toggle, and that is exactly
         * what [BoardFlow] insets — the rows below it run the full width of the screen.
         */
        Box(Modifier.fillMaxWidth()) {
            if (expanded) {
                ExpandedBoards(
                    slots = if (editing) draft else boards.map { BoardSlot(it, parked = false) },
                    selectedSlug = selectedSlug,
                    editing = editing,
                    // Clear of the toggle, its own end inset, and a pill's worth of breathing room.
                    firstRowInset = ToggleSlotWidth + Spacing.sm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.lg),
                    onBoardClick = { slug ->
                        onBoardClick(slug)
                        expanded = false
                    },
                    onEnterEditing = {
                        editing = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onReorder = { moved ->
                        draft = moved
                        commit(moved)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onTogglePark = ::togglePark,
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(count = boards.size, key = { boards[it].slug ?: FRONT_PAGE_KEY }) { index ->
                            val board = boards[index]
                            BoardPill(
                                board = board,
                                selected = board.slug == selectedSlug,
                                onClick = { onBoardClick(board.slug) },
                            )
                        }
                    }
                    // The scrolling row stops where the toggle starts, rather than running under it.
                    Spacer(Modifier.width(ToggleSlotWidth))
                }
            }
            // The band a pill occupies is 48dp — a 32dp shape centred inside the touch target Material
            // reserves for it — while this button is pinned to 32dp and would otherwise hang 8dp above
            // the first row of pills it is supposed to sit on. Giving it the same band, from the same
            // composition local the pills read, aligns the two shapes by construction rather than by a
            // hardcoded offset that a theme could invalidate.
            //
            // The end inset is the same 16dp the pills are laid out against on the left, so the strip
            // is symmetric: the button used to sit flush against the display edge because it was the
            // only thing in this row without padding of its own.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                    .padding(end = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                // The tonal button *is* the pill, rather than a plain IconButton with one drawn inside
                // it: the ripple is clipped to the shape it draws instead of spilling above and below
                // the 32dp pill.
                //
                // While editing it is the way out, so it turns into a tick. One control, because a
                // separate 完成 button would mean two things in a corner that only ever does one.
                val container by animateColorAsState(
                    targetValue =
                    if (editing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "board-toggle-container",
                )
                val content by animateColorAsState(
                    targetValue =
                    if (editing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "board-toggle-content",
                )
                FilledTonalIconButton(
                    onClick = {
                        when {
                            editing -> editing = false
                            else -> expanded = !expanded
                        }
                    },
                    modifier = Modifier.size(width = ToggleWidth, height = 32.dp),
                    shape = CircleShape,
                    colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = container,
                        contentColor = content,
                    ),
                ) {
                    // Three states through one 40dp hole, so they trade places rather than cut. The
                    // specs are read out here because a transition spec is not a composable scope.
                    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                    AnimatedContent(
                        targetState = editing to expanded,
                        transitionSpec = {
                            (fadeIn(fade) + scaleIn(pop, initialScale = ICON_SWAP_SCALE))
                                .togetherWith(fadeOut(fade) + scaleOut(pop, targetScale = ICON_SWAP_SCALE))
                        },
                        label = "board-toggle-icon",
                    ) { (isEditing, isExpanded) ->
                        Icon(
                            imageVector =
                            when {
                                isEditing -> Icons.Default.Check
                                isExpanded -> Icons.Default.KeyboardArrowUp
                                else -> Icons.Default.KeyboardArrowDown
                            },
                            contentDescription =
                            stringResource(
                                when {
                                    isEditing -> R.string.action_finish_editing_boards
                                    isExpanded -> R.string.action_hide_all_boards
                                    else -> R.string.action_show_all_boards
                                },
                            ),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(visible = editing) {
            Text(
                text = stringResource(R.string.board_edit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The wrapped grid of pills, and — while [editing] — the drag surface over it.
 *
 * Both gestures live on this container rather than on the pills. A chip's own `clickable` consumes
 * the pointer down, so a long-press detector sitting on a chip would never see one; watching from the
 * parent on the initial pass is the only place both gestures can be read without fighting the chips
 * for their taps. It is also simply less machinery: one detector instead of fifteen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExpandedBoards(
    slots: List<BoardSlot>,
    selectedSlug: String?,
    editing: Boolean,
    firstRowInset: Dp,
    modifier: Modifier,
    onBoardClick: (String?) -> Unit,
    onEnterEditing: () -> Unit,
    onReorder: (List<BoardSlot>) -> Unit,
    onTogglePark: (String) -> Unit,
) {
    // Where each pill's *slot* is, in this container's coordinates. Recorded on the outer box of each
    // pill, which carries neither the drag transform nor the placement animation, so a pill gliding
    // under the finger cannot feed its own offset back into the hit testing that decides where it
    // lands. These are settled positions by construction.
    val slotBounds = remember { mutableStateMapOf<String, Rect>() }
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    // Finger position and where inside the pill it grabbed, both in container coordinates.
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabWithinPill by remember { mutableStateOf(Offset.Zero) }

    /*
     * Letting go is its own animation.
     *
     * The pill is held to the finger by an offset from its slot, so dropping it by simply forgetting
     * the drag would teleport it home. Instead the key stays live for one more animation and the
     * offset is scaled to zero, which glides it into the slot it earned — including a slot it was
     * reordered into on the way there, since the offset is recomputed from the current slot each
     * frame rather than baked in at release.
     */
    val scope = rememberCoroutineScope()
    val settle = remember { Animatable(0f) }
    var releasingKey by remember { mutableStateOf<String?>(null) }
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val liftSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    fun release() {
        val key = draggedKey ?: return
        draggedKey = null
        releasingKey = key
        scope.launch {
            settle.snapTo(1f)
            settle.animateTo(0f, settleSpec)
            if (releasingKey == key) releasingKey = null
        }
    }

    // A pill that leaves the list mid-drag — a board list refresh, an editor closed by the back key —
    // must not leave a floating ghost behind.
    if (draggedKey != null && slots.none { it.key == draggedKey }) draggedKey = null

    val gestures =
        if (editing) {
            Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        pointer = start
                        releasingKey = null
                        val slot =
                            slots.firstOrNull { slotBounds[it.key]?.contains(start) == true }
                        // Locked 综合 stays first, and reordering the parked tail would be sorting a
                        // bin. Both are still perfectly tappable; they are just not draggable.
                        draggedKey = slot?.takeIf { !it.locked && !it.parked }?.key
                        grabWithinPill =
                            draggedKey?.let { key -> start - (slotBounds[key]?.center ?: start) }
                                ?: Offset.Zero
                    },
                    onDrag = { change, delta ->
                        if (draggedKey == null) return@detectDragGestures
                        change.consume()
                        pointer += delta
                        val moved = slots.reorderedFor(draggedKey, pointer, slotBounds)
                        if (moved != null) onReorder(moved)
                    },
                    onDragEnd = ::release,
                    onDragCancel = ::release,
                )
            }
        } else {
            Modifier.longPressToEdit(onEnterEditing)
        }

    // Lookahead is what lets a pill animate into a slot it has already been placed in: the layout
    // jumps, the drawing follows. Every pill below reads this scope through `animateBounds`.
    LookaheadScope {
        BoardFlow(
            firstRowInset = firstRowInset,
            // Editing widens the gaps so the corner badges have somewhere to sit: at the resting 8dp
            // a badge would overlap the pill beside it rather than the pill it belongs to.
            horizontalGap = if (editing) Spacing.lg else Spacing.sm,
            modifier = modifier
                .onGloballyPositioned { containerCoords = it }
                .then(gestures),
        ) {
            slots.forEach { slot ->
                // Identity, not position. Without it Compose would reuse each node for whatever pill
                // now sits at that index — the content would teleport and there would be nothing left
                // for the placement animation to animate.
                key(slot.key) {
                    val active = slot.key == draggedKey
                    val held = active || slot.key == releasingKey
                    val lift = animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = liftSpec,
                        label = "board-pill-lift",
                    )
                    Box(
                        modifier = Modifier
                            // The held pill draws over its neighbours instead of sliding beneath them.
                            .zIndex(if (held) 1f else 0f)
                            .onGloballyPositioned { coords ->
                                val container = containerCoords ?: return@onGloballyPositioned
                                slotBounds[slot.key] = container.localBoundingBoxOf(coords)
                            },
                    ) {
                        Box(
                            // Inner node, so neither transform below is visible to the measurement
                            // above. A held pill is already glued to the finger, so it is the one
                            // pill that must *not* animate towards its slot.
                            modifier = (
                                if (held) {
                                    Modifier
                                } else {
                                    Modifier.animateBounds(this@LookaheadScope)
                                }
                                ).graphicsLayer {
                                if (held) {
                                    val home = slotBounds[slot.key]
                                    if (home != null) {
                                        val factor = if (active) 1f else settle.value
                                        val target = pointer - grabWithinPill
                                        translationX = (target.x - home.center.x) * factor
                                        translationY = (target.y - home.center.y) * factor
                                    }
                                }
                                val raised = lift.value
                                if (raised > 0f) {
                                    val scale = 1f + (DRAG_SCALE - 1f) * raised
                                    scaleX = scale
                                    scaleY = scale
                                    shadowElevation = DRAG_ELEVATION.toPx() * raised
                                    shape = CircleShape
                                    clip = false
                                }
                            },
                        ) {
                            BoardPill(
                                board = slot.board,
                                selected = !editing && slot.board.slug == selectedSlug,
                                parked = slot.parked,
                                onClick = { if (!editing) onBoardClick(slot.board.slug) },
                            )
                            AnimatedVisibility(
                                visible = editing && !slot.locked,
                                enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) +
                                    fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                ParkBadge(
                                    parked = slot.parked,
                                    onClick = { onTogglePark(slot.key) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pills wrapped onto as many lines as they need, with [firstRowInset] kept clear at the end of the
 * first line only.
 *
 * `FlowRow` cannot express that, and the difference is the whole reason this exists: the expand
 * toggle shares a line with the first row of pills, so reserving its width by putting it in a `Row`
 * beside the flow narrowed *every* line and left a tall empty gutter down the right of the block. One
 * line gives way to the toggle; the rest run the full width.
 *
 * Deliberately minimal — no vertical gap parameter, no alignment, no `maxLines`. Each pill is a 32dp
 * shape centred in a 48dp touch band, so the lines already clear each other by 16dp and anything this
 * layout added on top would read as a list rather than as a grid of chips.
 */
@Composable
private fun BoardFlow(
    firstRowInset: Dp,
    horizontalGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = horizontalGap.roundToPx()
        val width = constraints.maxWidth
        val inset = firstRowInset.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        val rows = mutableListOf<List<Placeable>>()
        var row = mutableListOf<Placeable>()
        var rowWidth = 0
        placeables.forEach { placeable ->
            // Only the first line has to leave room for the toggle sitting on it.
            val limit = if (rows.isEmpty()) width - inset else width
            val extended = if (row.isEmpty()) placeable.width else rowWidth + gap + placeable.width
            // A pill that does not fit even on a line of its own still has to go somewhere, so an
            // empty row always accepts one rather than looping forever looking for a wider line.
            if (row.isNotEmpty() && extended > limit) {
                rows += row
                row = mutableListOf(placeable)
                rowWidth = placeable.width
            } else {
                row += placeable
                rowWidth = extended
            }
        }
        if (row.isNotEmpty()) rows += row

        layout(width, rows.sumOf { line -> line.maxOf { it.height } }) {
            var y = 0
            rows.forEach { line ->
                var x = 0
                line.forEach { placeable ->
                    placeable.place(x, y)
                    x += placeable.width + gap
                }
                y += line.maxOf { it.height }
            }
        }
    }
}

/**
 * The ✕ / ＋ on a pill's shoulder.
 *
 * One control with two meanings rather than two controls, because parking and restoring are the same
 * decision seen from either side, and the pill is 32dp tall — there is room for exactly one badge.
 */
@Composable
private fun ParkBadge(
    parked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (parked) R.string.board_restore else R.string.board_park)
    val container by animateColorAsState(
        targetValue = if (parked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "park-badge-container",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(BADGE_SIZE)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = container,
        contentColor =
        if (parked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The two glyphs are the same stroke rotated, so they turn into each other rather than
            // cutting — the badge is 20dp and a cut at that size reads as a flicker.
            val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            AnimatedContent(
                targetState = parked,
                transitionSpec = {
                    (fadeIn(fade) + scaleIn(pop, initialScale = ICON_SWAP_SCALE))
                        .togetherWith(fadeOut(fade) + scaleOut(pop, targetScale = ICON_SWAP_SCALE))
                },
                label = "park-badge-icon",
            ) { isParked ->
                Icon(
                    imageVector = if (isParked) Icons.Default.Add else Icons.Default.Close,
                    // The Surface already carries the description; repeating it here would have
                    // TalkBack read the badge twice.
                    contentDescription = null,
                    modifier = Modifier.size(BADGE_ICON_SIZE),
                )
            }
        }
    }
}

@Composable
private fun BoardPill(
    board: Board,
    selected: Boolean,
    onClick: () -> Unit,
    parked: Boolean = false,
) {
    val parkedLabel = stringResource(R.string.board_parked)
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.graphics.Color>()
    // Parking a board is a move *and* a fade, and the two read as one gesture only if the colour
    // takes as long as the flight to the tail does.
    val container by animateColorAsState(
        targetValue =
        if (parked) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = colorSpec,
        label = "board-pill-container",
    )
    val label by animateColorAsState(
        targetValue =
        if (parked) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PARKED_ALPHA)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorSpec,
        label = "board-pill-label",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = board.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        },
        // A parked board is still a board — it is only out of the way — so it keeps its shape and its
        // label and loses its contrast. TalkBack gets told in words, since grey is not a word.
        modifier =
        if (parked) Modifier.semantics { contentDescription = "${board.title}, $parkedLabel" } else Modifier,
        // Boards the site refuses to anyone signed out are worth flagging before the tap, not after.
        // Described rather than decorative: the warning is the whole point of the icon, and it is
        // nowhere else in the chip, so leaving it null hides the restriction from TalkBack.
        trailingIcon =
        if (board.adminOnly) {
            {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.board_admin_only),
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
        shape = CircleShape,
        colors =
        FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = label,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = null,
    )
}

/**
 * One pill's place in the strip while it is being rearranged.
 *
 * [parked] rather than two lists: a drag and a park are both "move this slot", and splitting the
 * halves apart would mean every index in the reorder maths had to know which list it was counting in.
 */
internal data class BoardSlot(
    val board: Board,
    val parked: Boolean,
) {
    val key: String get() = board.slug ?: FRONT_PAGE_KEY

    /** 综合 is the front page rather than a board: it cannot be moved and it cannot be parked. */
    val locked: Boolean get() = board.slug == null
}

/**
 * The list with the held pill moved to whatever slot the finger is over, or null if nothing changed.
 *
 * Settled slot bounds and a plain hit test, rather than midpoint thresholds along an axis: the pills
 * wrap onto several rows, so "past halfway" has no single direction to be past halfway *in*.
 *
 * It settles by construction. After a move the held pill occupies the slot the finger is in, and the
 * only candidates considered are other pills, so a finger held still cannot swap back and forth.
 */
internal fun List<BoardSlot>.reorderedFor(
    draggedKey: String?,
    pointer: Offset,
    bounds: Map<String, Rect>,
): List<BoardSlot>? {
    val key = draggedKey ?: return null
    val from = indexOfFirst { it.key == key }
    if (from < 0) return null
    val to =
        indexOfFirst { slot ->
            // The parked tail is not a landing strip: dropping a pill there would park it silently,
            // and parking is what the badge is for. 综合 keeps the first slot.
            slot.key != key && !slot.locked && !slot.parked && bounds[slot.key]?.contains(pointer) == true
        }
    if (to < 0) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Fires once when a press is held without moving.
 *
 * Hand-rolled rather than `combinedClickable` because this sits *above* the chips: they consume the
 * pointer down for their own ripple, so anything watching on the main pass never sees a gesture
 * start. Reading the initial pass gets the press before the chip claims it, and watching for slop
 * means a scroll or a drag still cancels it.
 */
private fun Modifier.longPressToEdit(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val heldStill =
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travelled += change.positionChangeIgnoreConsumed()
                            if (travelled.getDistance() > viewConfiguration.touchSlop) break
                        }
                    }
                    // Lifted, or moved far enough to be a scroll: either way, not a long press.
                    false
                } catch (_: PointerEventTimeoutCancellationException) {
                    true
                }
            if (heldStill) onLongPress()
        }
    }

/** 综合 has no slug, and a list key has to be something. */
internal const val FRONT_PAGE_KEY = "front"

private val ToggleWidth = 40.dp

/** The toggle plus the end inset it is drawn against — the width the first row of pills gives up. */
private val ToggleSlotWidth = ToggleWidth + Spacing.lg

private const val DRAG_SCALE = 1.06f
private val DRAG_ELEVATION = 8.dp
private const val PARKED_ALPHA = 0.55f
private val BADGE_SIZE = 20.dp
private val BADGE_ICON_SIZE = 13.dp
private const val ICON_SWAP_SCALE = 0.7f
