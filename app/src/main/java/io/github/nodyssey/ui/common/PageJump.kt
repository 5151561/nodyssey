package io.github.nodyssey.ui.common

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite.COMMENTS_PER_PAGE
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

/*
 * The page control for the screens that read a paged site list as one continuous scroll.
 *
 * It exists because "append the next page while scrolling" alone is not enough on a list a hundred
 * pages deep: continuing to read is cheap, but *arriving* somewhere is not, and flinging is a poor
 * way to travel. The pairing — auto-append for reading, an explicit jump for travelling — was built
 * for the comment thread first; 管理记录 has the same shape (a numbered list far too long to scroll)
 * and now shares the control rather than growing a second dialect of it.
 *
 * Deliberately not a whole toolbar: the thread's rail sits above a 回复 FAB and the log's above
 * nothing at all, so each screen stacks [PageJumpRail] over whatever it has. What is shared is the
 * part that must not drift — the wording, the shortcuts, and what the numbers mean.
 */

/**
 * 翻页栏: the page you are on, with a step either side of it, stacked under the reader's thumb.
 *
 * A column of small keys rather than a `HorizontalFloatingToolbar`, which is what this was. The
 * toolbar owns the FAB that rides in it and swells that FAB to a round 80dp on collapse, so the one
 * control on the screen that must not move was in a different place every time the reader stopped
 * scrolling — and a full-width bar for three small controls read as furniture besides. Stacked, the
 * FAB is the screen's own and never resizes, and the keys sit on the side the thumb is already on.
 *
 * [expanded] false retracts 上一页 and 下一页 into the page key, which keeps its size throughout: the
 * page number is what a reader glances at mid-scroll, and it is also the tap that opens
 * [PageJumpSheet], so it is the one key that is never in the way.
 *
 * [page] is the page the reader is *looking at*, which on an appending list is not the last page
 * fetched — the caller derives it from whatever is at the top of the viewport.
 */
@Composable
fun PageJumpRail(
    expanded: Boolean,
    page: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // No arrangement spacing: each key paints 40dp inside a 48dp touch slot, and the 4dp of slack
    // that leaves on every side is exactly the 8dp gap the design draws between two keys. Asking for
    // 8dp on top of it would space them 16dp apart, and shrinking the slot to close the gap would
    // put a 40dp target under a thumb.
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PageKey(
            onClick = onPageClick,
            contentDescription = stringResource(R.string.page_jump_page_of, page, totalPages),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = page.toString(),
                    style = PageNumberStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.page_jump_of_total, totalPages),
                    style = PageTotalStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // Retracting upwards, into the page key rather than into the FAB below: the two keys belong
        // to the number they step, and the FAB is not theirs to grow out of.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motionScheme.fastSpatialSpec(), Alignment.Top) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec(), Alignment.Top) +
                fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PageKey(
                    onClick = onPrevious,
                    enabled = page > 1,
                    contentDescription = stringResource(R.string.page_jump_previous),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
                PageKey(
                    onClick = onNext,
                    enabled = page < totalPages,
                    contentDescription = stringResource(R.string.page_jump_next),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
    }
}

/** What a key paints. The rest of its [Sizes.minTouchTarget] slot is the gap to the next one. */
private val PageKeySize = 40.dp

/** Between `medium` and `large` on the shape scale, and on purpose: a squarer key than the FAB below. */
private val PageKeyShape = RoundedCornerShape(14.dp)

/** Enough to lift a key off a list that scrolls under it, and no more — the FAB below owns the corner. */
private val PageKeyElevation = 2.dp

private const val DISABLED_KEY_ALPHA = 0.38f

private val PageNumberStyle =
    TextStyle(
        fontSize = 13.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

private val PageTotalStyle =
    TextStyle(
        fontSize = 9.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    )

/**
 * One key of the rail: 40dp of paint, centred in a touch target that clears Material's minimum.
 *
 * [contentDescription] replaces whatever is inside rather than joining it, so the page key is read
 * as "第 2 / 12 页" and not as the two numbers it is drawn from.
 */
@Composable
private fun PageKey(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.size(Sizes.minTouchTarget), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = PageKeyShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            // Material's Surface leaves a disabled one looking exactly like a live one, so the end
            // of the run has to be said here: at page 1 上一页 is still drawn, and still does nothing.
            contentColor =
            if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_KEY_ALPHA)
            },
            shadowElevation = PageKeyElevation,
            modifier = Modifier.size(PageKeySize),
        ) {
            Box(
                modifier = Modifier
                    .size(PageKeySize)
                    .clearAndSetSemantics { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

/**
 * Where the reader can travel to, as one chip.
 *
 * The three lists that share this sheet mean different things by their own destinations — the thread's
 * newest is the foot of its last page, the feed's is page 1 — so each caller says what it means rather
 * than passing a flag the sheet would have to interpret.
 */
@Immutable
data class JumpDestination(
    val label: String,
    val icon: ImageVector,
    val onGo: () -> Unit,
)

/**
 * The jump sheet: every page as a key, and the destinations a number cannot name.
 *
 * A scroller rather than a field with a 前往 button under it. Travelling one or two pages is most of
 * what this is opened for, and typing "3" and confirming it is three taps for something that was
 * already on screen — so a page key *is* the jump, with no confirmation. Typing stays for the reader
 * going somewhere far, behind the [numberEntry] chip.
 *
 * [note] is the caller's own sentence because only the caller knows what it has loaded — the thread
 * counts 楼, the feed states the site's page size — and a shared component inventing a noun for both
 * would be wrong in one of them.
 *
 * [resume] and [newest] are chips only while they lead somewhere else: 最新 on page 1 of the feed, or
 * a resume offer pointing at the page under the reader's thumb, are taps that do nothing and read as
 * a broken control rather than as a satisfied one. The caller decides what they mean; whether they
 * are worth showing is decided here.
 *
 * [totalPages] of 1 or less takes the scroller away rather than drawing a single key: a list whose
 * page count never arrived should say so through [note] instead of showing a page count it made up.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PageJumpSheet(
    page: Int,
    totalPages: Int,
    note: String,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
    resume: JumpDestination? = null,
    newest: JumpDestination? = null,
    /** 页码 or 楼层 — the chip that swaps the scroller for a number to type, and what that number is. */
    numberEntry: NumberEntry = NumberEntry.Page,
    onGoToFloor: ((Int) -> Unit)? = null,
) {
    val lastPage = totalPages.coerceAtLeast(1)
    val current = page.coerceIn(1, lastPage)
    var typing by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        // Material's own handle reserves 22dp above and below the bar, which put 45dp of nothing
        // between the top of the sheet and its title. 8 / 4 / 8 — half the design's own bottom gap,
        // which still read as a band of nothing under a bar that is only there to be dragged.
        dragHandle = {
            Box(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm)) {
                Box(
                    modifier = Modifier
                        .size(width = DragHandleWidth, height = DragHandleHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SheetPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.page_jump_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
            }

            if (lastPage > 1 && !typing) {
                PageScroller(current = current, lastPage = lastPage, onGo = onGo)
                PageProgress(current = current, lastPage = lastPage)
            }

            // Right-aligned, and wrapping rather than scrolling: the control this sheet belongs to
            // lives in the bottom-right corner, so its destinations belong on the same side as the
            // thumb that opened them. Anchoring the row's end also pins them — 上次阅读 is the one
            // chip that comes and goes, and from the left it moved every other chip with it.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SheetPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                resume?.let { JumpChip(it.icon, it.label, it.onGo, highlighted = true) }
                newest?.let { JumpChip(it.icon, it.label, it.onGo) }
                JumpChip(
                    icon = PlazaIcons.Dialpad,
                    label = stringResource(numberEntry.label),
                    onClick = { typing = !typing },
                    highlighted = typing,
                )
            }

            if (typing) {
                PageNumberField(
                    page = current,
                    lastPage = lastPage,
                    numberEntry = numberEntry,
                    onGo = onGo,
                    onGoToFloor = onGoToFloor,
                )
            }
        }
    }
}

/** What the sheet's number field takes, which is not the same unit on every list. */
enum class NumberEntry(
    @get:StringRes val label: Int,
) {
    Page(R.string.page_jump_by_page),
    Floor(R.string.page_jump_by_floor),
}

/**
 * Every page, as keys, with the one the reader is on twice the weight of the rest.
 *
 * Lazy because a board can run to several hundred pages, and started at the current key rather than
 * at page 1 — the reader opened this from a control that says "第 40 页", and a row that begins a
 * thousand pixels away from that number reads as the wrong list.
 */
@Composable
private fun PageScroller(
    current: Int,
    lastPage: Int,
    onGo: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(current, lastPage) {
        // Off by one so the page before the current one stays in view: the two commonest jumps from
        // here are one step either way, and a key flush against the left edge looks like the end.
        state.scrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = SheetPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(count = lastPage, key = { it }) { index ->
            val number = index + 1
            PageScrollerKey(number = number, selected = number == current, onClick = { onGo(number) })
        }
    }
}

@Composable
private fun PageScrollerKey(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = if (selected) SelectedPageKeyShape else ScrollerPageKeyShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (selected) SelectedPageKeyElevation else 0.dp,
        modifier = Modifier
            .size(
                width = if (selected) SelectedPageKeyWidth else ScrollerPageKeyWidth,
                height = ScrollerPageKeyHeight,
            ).semantics { this.selected = selected },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style =
                if (selected) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleSmall
                }.copy(fontFeatureSettings = TABULAR_FIGURES),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** How far through the list the current page is, and how many there are — the scroller cannot show both. */
@Composable
private fun PageProgress(
    current: Int,
    lastPage: Int,
) {
    Row(
        modifier = Modifier.padding(horizontal = SheetPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ProgressTrackHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(current.toFloat() / lastPage)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            stringResource(R.string.page_jump_total_pages, lastPage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The far jump, typed.
 *
 * Committed by the keyboard's own 前往 key rather than by a button beside it: the keyboard is over
 * the sheet the whole time this is open, and a button under the field is a button under the keyboard.
 */
@Composable
private fun PageNumberField(
    page: Int,
    lastPage: Int,
    numberEntry: NumberEntry,
    onGo: (Int) -> Unit,
    onGoToFloor: ((Int) -> Unit)?,
) {
    var input by rememberSaveable { mutableStateOf(if (numberEntry == NumberEntry.Floor) "" else page.toString()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val number = input.toIntOrNull()
    val go = {
        when {
            number == null -> Unit
            numberEntry == NumberEntry.Floor -> onGoToFloor?.invoke(number.coerceAtLeast(0)) ?: Unit
            else -> onGo(number.coerceIn(1, lastPage))
        }
    }
    OutlinedTextField(
        value = input,
        onValueChange = { typed -> input = typed.filter { it.isDigit() } },
        label = {
            Text(
                when (numberEntry) {
                    NumberEntry.Floor -> stringResource(R.string.page_jump_floor_input, lastPage * COMMENTS_PER_PAGE)
                    NumberEntry.Page -> stringResource(R.string.page_jump_input, lastPage)
                },
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { go() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SheetPadding)
            .focusRequester(focus),
    )
}

private val SheetPadding = 20.dp
private val ScrollerPageKeyWidth = 48.dp
private val SelectedPageKeyWidth = 60.dp
private val ScrollerPageKeyHeight = 60.dp
private val ScrollerPageKeyShape = RoundedCornerShape(16.dp)
private val SelectedPageKeyShape = RoundedCornerShape(20.dp)
private val SelectedPageKeyElevation = 3.dp
private val ProgressTrackHeight = 3.dp
private val JumpChipHeight = 40.dp
private val JumpChipShape = RoundedCornerShape(14.dp)
private val JumpChipIconSize = 18.dp

/**
 * 13sp and 10dp of side padding, both a step under the label scale and the design's own chip.
 *
 * The three chips have to share one line on a 360dp screen — the commonest phone there is — and at
 * Material's `labelLarge` they came to 388dp and wrapped 楼层 onto a second row. They still wrap when
 * the reader has scaled their text up, which is the case worth wrapping for.
 */
private val JumpChipFontSize = 13.sp
private val JumpChipPadding = 10.dp

private val DragHandleWidth = 32.dp
private val DragHandleHeight = 4.dp

/**
 * One destination on the jump sheet.
 *
 * Hand-drawn rather than an `AssistChip` because the design's chip is neither of Material's: 40dp
 * tall against the chip scale's 32, and a 14dp corner against its 8 — the same corner the rail's keys
 * take, which is what makes the sheet read as the control's own rather than as a dialog it opened.
 */
@Composable
private fun JumpChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = JumpChipShape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor =
        if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (highlighted) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(JumpChipHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = JumpChipPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(JumpChipIconSize),
                tint = if (highlighted) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = JumpChipFontSize),
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
