package io.github.nodyssey.ui.postlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.nodyssey.data.Board
import io.github.nodyssey.ui.common.boardFamilyColors
import io.github.nodyssey.ui.common.boardFamilyOf
import io.github.nodyssey.ui.common.longPressToEdit
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_hide_all_boards
import io.github.nodyssey.ui.resources.action_show_all_boards
import io.github.nodyssey.ui.resources.board_admin_only
import io.github.nodyssey.ui.resources.board_edit_done
import io.github.nodyssey.ui.resources.board_edit_hint
import io.github.nodyssey.ui.resources.board_edit_title
import io.github.nodyssey.ui.resources.board_park
import io.github.nodyssey.ui.resources.board_parked
import io.github.nodyssey.ui.resources.board_parked_title
import io.github.nodyssey.ui.resources.board_restore
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaChipDefaults
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.cardBorder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

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
 * Expanded, a long press turns the same pills into 1j's editor: a 首页版块 header with 完成, the
 * strip's pills with a × to take one off, and 未加入首页 underneath holding the ones taken off, each
 * a tap from coming back. Drag to reorder. Parked boards are only drawn while editing — out of the
 * way is the whole point of parking one — so the editor is also the only place they can be
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
    // Index 0 counts: 综合 is the first pill, and picking it from a strip scrolled halfway along
    // leaves it off screen to the left unless the row is sent back to the head.
    val selectedIndex = boards.indexOfFirst { it.slug == selectedSlug }
    LaunchedEffect(expanded, selectedIndex) {
        if (!expanded && selectedIndex >= 0) rowState.animateScrollToItem(selectedIndex)
    }

    // Back is what every other transient mode on this screen answers to, and the editor is one.
    PlazaBackHandler(enabled = editing) { editing = false }

    Column(
        Modifier
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            )
            // Air above the pills, whether the header is showing or folded away over them; the list's
            // own top padding makes up the rest below.
            .padding(top = Spacing.sm, bottom = Spacing.xs),
    ) {
        AnimatedVisibility(visible = editing) {
            BoardEditHeader(
                onDone = { editing = false },
                onCollapse = {
                    editing = false
                    expanded = false
                },
            )
        }
        /*
         * A box with the toggle laid over the corner, not a row with the toggle beside the pills.
         *
         * A row makes the toggle's width a column that every wrapped row of pills has to keep clear,
         * so an expanded strip left a tall empty gutter down its right edge with a single button at
         * the top of it. Only the *first* row shares its line with the toggle, and that is exactly
         * what [BoardFlow] insets — the rows below it run the full width of the screen. While editing
         * the toggle sits in the header instead, and the first row gets its width back.
         */
        Box(Modifier.fillMaxWidth()) {
            if (expanded) {
                val slots = if (editing) draft else boards.map { BoardSlot(it, parked = false) }
                ExpandedBoards(
                    slots = slots.filterNot { it.parked },
                    parkedSlots = slots.filter { it.parked },
                    selectedSlug = selectedSlug,
                    editing = editing,
                    firstRowInset = if (editing) 0.dp else ToggleWidth + PillGap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = ToggleEndInset),
                    onBoardClick = { slug ->
                        onBoardClick(slug)
                        expanded = false
                    },
                    onEnterEditing = {
                        editing = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onReorder = { moved ->
                        // The flow only holds the strip's half; the parked half rides along unchanged.
                        val next = moved + draft.filter { it.parked }
                        draft = next
                        commit(next)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onTogglePark = ::togglePark,
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(PillGap),
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
            // The same [PillHeight] band the pills are pinned to, so the button's shape sits on the
            // first row of pills rather than centred in a taller band beside them. Both are shorter
            // than 48dp; Compose extends a pointer target that small to 48dp when it hit-tests.
            //
            // The end inset keeps the button off the display edge, where it used to sit flush because
            // it was the only thing in this row without padding of its own.
            if (!editing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .height(PillHeight)
                        .padding(end = ToggleEndInset),
                    contentAlignment = Alignment.Center,
                ) {
                    BoardToggle(expanded = expanded, onClick = { expanded = !expanded })
                }
            }
        }
    }
}

/**
 * 1j's header over the editor: what is being edited, 完成, and the toggle — now saying 收起 — moved
 * up out of the pills' first row. 完成 leaves the strip open on the result; the toggle folds it away
 * as well, which is what someone reaching for the same corner they opened it from expects.
 */
@Composable
private fun BoardEditHeader(
    onDone: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = ToggleEndInset, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.board_edit_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Material's extra-small step, the size the Lean round gives the composer's 发布: the one
            // filled thing in the header without being the tallest.
            Button(
                onClick = onDone,
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
            ) {
                Text(stringResource(Res.string.board_edit_done))
            }
            BoardToggle(expanded = true, onClick = onCollapse)
        }
        Text(
            text = stringResource(Res.string.board_edit_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.sm),
        )
    }
}

/**
 * The ⌄ / ⌃ that opens and folds the strip.
 *
 * The tonal button *is* the pill, rather than a plain IconButton with one drawn inside it: the ripple
 * is clipped to the shape it draws instead of spilling above and below the pill.
 */
@Composable
private fun BoardToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    FilledTonalIconButton(
        onClick = onClick,
        // 墨水屏 draws the raised tone as the page's own paper, and the outline is all that shows a
        // key there.
        modifier = Modifier.size(width = ToggleWidth, height = PillHeight).cardBorder(layers, PillShape),
        shape = PillShape,
        colors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = layers.raised,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        // The two states through one hole, so they trade places rather than cut. The specs are read
        // out here because a transition spec is not a composable scope.
        val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(fade) + scaleIn(pop, initialScale = ICON_SWAP_SCALE))
                    .togetherWith(fadeOut(fade) + scaleOut(pop, targetScale = ICON_SWAP_SCALE))
            },
            label = "board-toggle-icon",
        ) { isExpanded ->
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription =
                stringResource(if (isExpanded) Res.string.action_hide_all_boards else Res.string.action_show_all_boards),
                modifier = Modifier.size(ToggleIconSize),
            )
        }
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
    /** The boards taken off the strip — only ever non-empty while [editing]. */
    parkedSlots: List<BoardSlot>,
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

    /*
     * Read at event time, not captured. `pointerInput(Unit)` keeps the block it started with for as
     * long as the node lives, so a drag that closed over `slots` would work on the strip as it was at
     * the editor's first touch — before a park, or before the previous drag — and land the pill by
     * slots that have since moved. `slots` and `slotBounds` both change with the same frame, so
     * reading the current one keeps the list and the hit test in step.
     */
    val currentSlots by rememberUpdatedState(slots)
    val reorder by rememberUpdatedState(onReorder)
    val editingNow by rememberUpdatedState(editing)
    val enterEditing by rememberUpdatedState(onEnterEditing)

    /*
     * The long press stays on the flow while editing, switched off, rather than giving way to the drag
     * detector: the finger that opened the editor is still down when it opens, and the long press is
     * what swallows that finger's lift. Swapped out, it would be gone before the lift, and the pill
     * under the finger would take the lift as a tap — which in the editor parks it.
     */
    val gestures =
        Modifier
            .longPressToEdit(enabled = { !editingNow }) { enterEditing() }
            .then(
                if (editing) {
                    Modifier.pointerInput(Unit) {
                        var lastSent: List<BoardSlot>? = null
                        detectDragGestures(
                            onDragStart = { start ->
                                pointer = start
                                releasingKey = null
                                lastSent = null
                                val slot =
                                    currentSlots.firstOrNull { slotBounds[it.key]?.contains(start) == true }
                                // Locked 综合 stays first. It is still perfectly tappable; it is just
                                // not draggable. (The parked boards are not in this flow at all.)
                                draggedKey = slot?.takeIf { !it.locked && !it.parked }?.key
                                grabWithinPill =
                                    draggedKey?.let { key -> start - (slotBounds[key]?.center ?: start) }
                                        ?: Offset.Zero
                            },
                            onDrag = { change, delta ->
                                if (draggedKey == null) return@detectDragGestures
                                change.consume()
                                pointer += delta
                                val moved = currentSlots.reorderedFor(draggedKey, pointer, slotBounds)
                                // Several moves can land before the strip recomposes, and each would
                                // work out the same swap again from the same frame's list.
                                if (moved != null && moved != lastSent) {
                                    lastSent = moved
                                    reorder(moved)
                                }
                            },
                            onDragEnd = ::release,
                            onDragCancel = ::release,
                        )
                    }
                } else {
                    Modifier
                },
            )

    // Lookahead is what lets a pill animate into a slot it has already been placed in: the layout
    // jumps, the drawing follows. Every pill below reads this scope through `animateBounds`.
    Column(modifier) {
        LookaheadScope {
            BoardFlow(
                firstRowInset = firstRowInset,
                horizontalGap = PillGap,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { containerCoords = it }
                    .then(gestures),
            ) {
                slots.forEach { slot ->
                    // Identity, not position. Without it Compose would reuse each node for whatever pill
                    // now sits at that index — the content would teleport and there would be nothing left
                    // for the placement animation to animate.
                    key(slot.key) {
                        // A pill that leaves the flow — parked, or gone in a refresh — takes its
                        // slot with it, so nothing is ever dropped onto where it used to be.
                        DisposableEffect(slot.key) { onDispose { slotBounds.remove(slot.key) } }
                        val active = slot.key == draggedKey
                        val held = active || slot.key == releasingKey
                        val eink = LocalEinkMode.current
                        val pillShape = PillShape
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
                                        // And tipped a few degrees, as 1j draws the held pill.
                                        rotationZ = DRAG_TILT_DEGREES * raised
                                        // The scale above already says "picked up"; the shadow is
                                        // what a screen adds to it, and paper has nothing to add.
                                        shadowElevation =
                                            if (eink) 0f else DRAG_ELEVATION.toPx() * raised
                                        shape = pillShape
                                        clip = false
                                    }
                                },
                            ) {
                                BoardPill(
                                    board = slot.board,
                                    selected = slot.board.slug == selectedSlug,
                                    // 综合 is not a board and cannot be taken off, so it gets no ×.
                                    editing = editing && !slot.locked,
                                    onClick = {
                                        when {
                                            !editing -> onBoardClick(slot.board.slug)
                                            !slot.locked -> onTogglePark(slot.key)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        // 1j's second block: what is off the strip, a tap each from coming back. Outside the flow
        // above rather than a greyed tail at the end of it, so the strip's own rows show exactly what
        // the strip will hold.
        if (parkedSlots.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.board_parked_title),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm).semantics { heading() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PillGap),
                verticalArrangement = Arrangement.spacedBy(PillGap),
            ) {
                parkedSlots.forEach { slot ->
                    key(slot.key) {
                        ParkedBoardChip(board = slot.board, onClick = { onTogglePark(slot.key) })
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
 * Deliberately minimal — no alignment, no `maxLines`. The lines are [PillGap] apart, the same as
 * the pills along one: a pill is laid out at its painted height, so without it the lines would touch.
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
        val lineGap = PillGap.roundToPx()
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

        val height = rows.sumOf { line -> line.maxOf { it.height } } + lineGap * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEach { line ->
                var x = 0
                line.forEach { placeable ->
                    placeable.place(x, y)
                    x += placeable.width + gap
                }
                y += line.maxOf { it.height } + lineGap
            }
        }
    }
}

/**
 * One board on the strip, in its board family's colours — the same four the tags on every row use:
 * tonal at rest, filled once picked. 综合 is the front page rather than a board, belongs to no family,
 * and keeps the neutral chip and the inverse fill the app's other chips select with.
 *
 * While [editing] it gains a × at its end, and a tap takes it off the strip rather than opening it:
 * 1j's editor, where picking a board is not what the pills are for. The colours stay as they are, so
 * the editor opens on the same strip rather than a recoloured one.
 */
@Composable
private fun BoardPill(
    board: Board,
    selected: Boolean,
    onClick: () -> Unit,
    editing: Boolean = false,
) {
    val colors =
        if (board.slug == null) {
            PlazaChipDefaults.filterChipColors()
        } else {
            val family = boardFamilyColors(boardFamilyOf(board.slug, board.title))
            PlazaChipDefaults
                .filterChipColors(containerColor = family.container, labelColor = family.content, iconColor = family.content)
                .copy(
                    selectedContainerColor = family.accent,
                    selectedLabelColor = family.onAccent,
                    selectedLeadingIconColor = family.onAccent,
                    selectedTrailingIconColor = family.onAccent,
                )
        }
    val parkLabel = stringResource(Res.string.board_park)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = board.title, style = pillLabelStyle(selected), maxLines = 1) },
        // A chip reads as "select this"; while editing its tap does something else, and TalkBack says
        // what. A null action keeps the chip's own, and only names it.
        modifier =
        Modifier
            .height(PillHeight)
            .then(if (editing) Modifier.semantics { onClick(label = parkLabel, action = null) } else Modifier),
        trailingIcon =
        when {
            // Described, since the × is the only thing saying what a tap here does.
            editing -> {
                {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.board_park),
                        modifier = Modifier.size(PILL_ICON_SIZE),
                    )
                }
            }

            // Boards the site refuses to anyone signed out are worth flagging before the tap, not
            // after. Described rather than decorative: the warning is the whole point of the icon,
            // and it is nowhere else in the chip, so leaving it null hides the restriction from
            // TalkBack.
            board.adminOnly -> {
                {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(Res.string.board_admin_only),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            else -> null
        },
        shape = PillShape,
        colors = colors,
        border = PlazaChipDefaults.border(selected),
    )
}

/**
 * A board off the strip, under 未加入首页: a dashed outline and a ＋, and a tap puts it back.
 *
 * The outline is drawn rather than handed to the chip: Material's chips take a `BorderStroke`, which
 * has no dash, and a solid outline would make these read as one more row of the strip. A board the
 * site locks to signed-in readers shows its lock where the ＋ would be, as 1j draws 内版 — it can
 * still be put back.
 */
@Composable
private fun ParkedBoardChip(
    board: Board,
    onClick: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = PillShape
    val parked = stringResource(Res.string.board_parked)
    val restore = stringResource(Res.string.board_restore)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The dash goes on a box around the chip, not on the chip's own modifier: that modifier sits
    // outside the chip's 48dp touch-target padding, so a line drawn there traces the touch target —
    // taller than the pill, and into the row below.
    Box(
        Modifier
            .height(PillHeight)
            .drawBehind {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = outline,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            },
    ) {
        AssistChip(
            onClick = onClick,
            label = { Text(text = board.title, style = pillLabelStyle(selected = false), maxLines = 1) },
            leadingIcon = {
                Icon(
                    if (board.adminOnly) Icons.Default.Lock else Icons.Default.Add,
                    contentDescription = if (board.adminOnly) stringResource(Res.string.board_admin_only) else null,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            },
            // A dashed outline is not a word: TalkBack is told the board is off the strip, and what a
            // tap does about it.
            modifier =
            Modifier.height(PillHeight).semantics {
                contentDescription = "${board.title}, $parked"
                onClick(label = restore, action = null)
            },
            shape = shape,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color.Transparent,
                labelColor = muted,
                leadingIconContentColor = muted,
            ),
            border = null,
        )
    }
}

@Composable
private fun pillLabelStyle(selected: Boolean) =
    MaterialTheme.typography.labelLarge.copy(
        fontSize = PILL_LABEL_SIZE,
        lineHeight = PILL_LABEL_LINE_HEIGHT,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
    )

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

/** 综合 has no slug, and a list key has to be something. */
internal const val FRONT_PAGE_KEY = "front"

private val ToggleWidth = 32.dp

/** Where the toggle stops short of the display edge. */
private val ToggleEndInset = Spacing.sm

private val ToggleIconSize = 18.dp

/**
 * A board pill is the app's chip — a soft rectangle rather than a capsule, so a row of them reads as
 * tabs — a step under [PlazaChipDefaults.Height] and its corner, as the Lean round's 2a draws the strip:
 * this is navigation the reader passes over on every screen of the feed, not a form. Compose still
 * hit-tests a pill this short at 48dp.
 */
private val PillHeight = 30.dp

private val PillShape
    @Composable get() = MaterialTheme.shapes.small

private val PillGap = 6.dp

private val PILL_LABEL_SIZE = 13.sp
private val PILL_LABEL_LINE_HEIGHT = 18.sp

/** The toggle plus the end inset it is drawn against — the width the first row of pills gives up. */
private val ToggleSlotWidth = ToggleWidth + ToggleEndInset

private val PILL_ICON_SIZE = 16.dp

private const val DRAG_SCALE = 1.06f
private const val DRAG_TILT_DEGREES = -3f
private val DRAG_ELEVATION = 8.dp
private const val ICON_SWAP_SCALE = 0.7f
