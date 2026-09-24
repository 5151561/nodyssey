package io.github.nodyssey.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.core.NodeSeekSite.COMMENTS_PER_PAGE
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.page_jump_by_floor
import io.github.nodyssey.ui.resources.page_jump_by_page
import io.github.nodyssey.ui.resources.page_jump_floor_input
import io.github.nodyssey.ui.resources.page_jump_go
import io.github.nodyssey.ui.resources.page_jump_input
import io.github.nodyssey.ui.resources.page_jump_next
import io.github.nodyssey.ui.resources.page_jump_of_total
import io.github.nodyssey.ui.resources.page_jump_page_of
import io.github.nodyssey.ui.resources.page_jump_previous
import io.github.nodyssey.ui.resources.page_jump_switch_unit
import io.github.nodyssey.ui.resources.page_jump_title
import io.github.nodyssey.ui.resources.page_jump_to
import io.github.nodyssey.ui.resources.page_jump_unit_floor
import io.github.nodyssey.ui.resources.page_jump_unit_page
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.TonalTile
import io.github.plaza.designsys.theme.ControlShape
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.floatShadow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
 * One card holding 上一页, the page and 下一页 in reading order, as the Lean round's 2b draws it, rather
 * than three floating keys: the three are one control, and one lifted shape says so with a third of
 * the shadow.
 *
 * [expanded] false folds 上一页 and 下一页 away into the page, which stays put: the page number is
 * what a reader glances at mid-scroll, and it is also the tap that opens [PageJumpSheet], so it is the
 * one part that is never in the way.
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
    val layers = LocalPlazaLayers.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        // The raised layer, not the page's grey: the rail floats over cards and over the gaps
        // between them, and a card the colour of the gaps disappeared into them there.
        color = layers.raised,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = shape,
        // On paper the shadow cannot be drawn, so the outline takes its job over.
        border = layers.cardBorderStroke,
        modifier = modifier
            .width(RailWidth)
            .floatShadow(shape, layers.shadows),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Folding towards the page from either side, so the number is where it was.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motionScheme.fastSpatialSpec(), Alignment.Bottom) +
                    fadeIn(motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(motionScheme.fastSpatialSpec(), Alignment.Bottom) +
                    fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                RailStep(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.page_jump_previous),
                    enabled = page > 1,
                    onClick = onPrevious,
                )
            }
            PageLabel(page = page, totalPages = totalPages, onClick = onPageClick, expanded = expanded)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motionScheme.fastSpatialSpec(), Alignment.Top) +
                    fadeIn(motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(motionScheme.fastSpatialSpec(), Alignment.Top) +
                    fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                RailStep(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.page_jump_next),
                    enabled = page < totalPages,
                    onClick = onNext,
                )
            }
        }
    }
}

/** The rail's width, and the height of each step in it. Compose hit-tests each at 48dp regardless. */
private val RailWidth = 40.dp

private val RailIconSize = 18.dp

/**
 * The page as `1/3`. Alone in the card once the steps fold away, it keeps the card from shrinking to
 * a sliver by standing a step's height; between the steps, the steps' own height is its margin.
 */
private val RetractedLabelHeight = 32.dp

private const val DISABLED_KEY_ALPHA = 0.38f

private val PageNumberStyle =
    TextStyle(
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

@Composable
private fun PageLabel(
    page: Int,
    totalPages: Int,
    onClick: () -> Unit,
    expanded: Boolean,
) {
    val description = stringResource(Res.string.page_jump_page_of, page, totalPages)
    val total = stringResource(Res.string.page_jump_of_total, totalPages)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val height by animateDpAsState(
        targetValue = if (expanded) 0.dp else RetractedLabelHeight,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "page-label-height",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildAnnotatedString {
                append(page.toString())
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Medium)) { append(total) }
            },
            style = PageNumberStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 上一页 or 下一页: a glyph in a square the rail's width, greyed rather than hidden at the end of the run. */
@Composable
private fun RailStep(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val eink = LocalEinkMode.current
    Box(
        modifier = Modifier
            .size(RailWidth)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            // Material leaves a disabled control looking like a live one unless told, so the end of
            // the run is said here: at page 1 上一页 is still drawn, and still does nothing.
            tint =
            if (enabled) {
                LocalContentColor.current
            } else {
                // Half-transparent ink is a grey the panel has to invent; `outlineVariant` is one it
                // already has, and it is the grey every other muted thing here uses.
                LocalContentColor.current.copy(alpha = DISABLED_KEY_ALPHA)
                    .takeIf { !eink } ?: MaterialTheme.colorScheme.outlineVariant
            },
            modifier = Modifier.size(RailIconSize),
        )
    }
}

/**
 * Where the reader can travel to, as one tile of the sheet's top row.
 *
 * The three lists that share this sheet mean different things by their own destinations — the thread's
 * newest is the foot of its last page, the feed's is page 1 — so each caller says what it means rather
 * than passing a flag the sheet would have to interpret.
 *
 * [detail] is the tile's second line: where the destination actually is (第 3 页 · #41). Optional, so a
 * caller that can only name the destination still gets a tile — the name then stands alone.
 */
@Immutable
data class JumpDestination(
    val label: String,
    val icon: ImageVector,
    val onGo: () -> Unit,
    val detail: String? = null,
)

/**
 * The jump sheet (9d): the destinations a number cannot name, every page as a key, and a field for
 * the far jump.
 *
 * Keys first and the field last, because travelling one or two pages is most of what this is opened
 * for: a key *is* the jump, with no confirmation, and typing "3" and confirming it is three taps for
 * something that was already on screen. The field is always there rather than behind a chip, but it
 * does not take focus — a keyboard rising over the keys every time the sheet opened would hide the
 * control this is mostly opened for.
 *
 * [note] is the caller's own sentence because only the caller knows what it has loaded — the thread
 * counts 楼, the feed states the site's page size — and a shared component inventing a noun for both
 * would be wrong in one of them. It sits under the field as the sheet's footnote.
 *
 * [resume] and [newest] are tiles only while they lead somewhere else: 最新 on page 1 of the feed, or
 * a resume offer pointing at the page under the reader's thumb, are taps that do nothing and read as
 * a broken control rather than as a satisfied one. The caller decides what they mean and whether they
 * are worth showing.
 *
 * [totalPages] of 1 or less takes the keys away rather than drawing a single one: a list whose page
 * count never arrived should say so through [note] instead of showing a page count it made up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageJumpSheet(
    page: Int,
    totalPages: Int,
    note: String,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
    resume: JumpDestination? = null,
    newest: JumpDestination? = null,
    /**
     * [NumberEntry.Floor] lets the field be switched from pages to floors, which only a list that can
     * land on a floor — the thread, through [onGoToFloor] — can honour. The field opens on pages
     * either way: that is the unit the keys above it are in.
     */
    numberEntry: NumberEntry = NumberEntry.Page,
    onGoToFloor: ((Int) -> Unit)? = null,
) {
    val lastPage = totalPages.coerceAtLeast(1)
    val current = page.coerceIn(1, lastPage)
    val title = stringResource(Res.string.page_jump_title)
    PlazaSheet(
        onDismiss = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(start = SheetPadding, end = SheetPadding, bottom = Spacing.xl)
                // The sheet no longer prints a heading — the design spends that line on the tiles —
                // so its name reaches a screen reader as the pane title instead.
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (resume != null || newest != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    resume?.let { JumpTile(it, Modifier.weight(1f)) }
                    newest?.let { JumpTile(it, Modifier.weight(1f)) }
                }
            }

            if (lastPage > 1) {
                PageKeys(current = current, lastPage = lastPage, onGo = onGo)
            }

            PageNumberField(
                page = current,
                lastPage = lastPage,
                floorsOffered = numberEntry == NumberEntry.Floor && onGoToFloor != null,
                onGo = onGo,
                onGoToFloor = onGoToFloor,
            )

            Text(
                note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the sheet's number field can take, which is not the same set on every list. */
enum class NumberEntry(
    val label: StringResource,
) {
    Page(Res.string.page_jump_by_page),
    Floor(Res.string.page_jump_by_floor),
}

/**
 * Every page, as keys five to a row's width, with the one the reader is on filled.
 *
 * Lazy because a board can run to several hundred pages, and scrolled so the current key is the third
 * of the five in view — the two commonest jumps from here are one step either way, and a key flush
 * against the left edge looks like the end of the list.
 */
@Composable
private fun PageKeys(
    current: Int,
    lastPage: Int,
    onGo: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    val layers = LocalPlazaLayers.current
    LaunchedEffect(current, lastPage) {
        state.scrollToItem((current - 3).coerceAtLeast(0))
    }
    // Measured rather than fixed: five keys fill the row exactly at any width, which is what makes
    // them read as a grid on a short thread and as a strip that continues on a long one.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val keyWidth = (maxWidth - Spacing.sm * (KEYS_IN_VIEW - 1)) / KEYS_IN_VIEW
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count = lastPage, key = { it }) { index ->
                val number = index + 1
                val selected = number == current
                // On paper the raised key is the page's own white; the outline is what makes it a key
                // there. The selected one is filled with `primary`, solid black on a panel, and needs
                // no help.
                TonalTile(
                    onClick = { onGo(number) },
                    containerColor = if (selected) MaterialTheme.colorScheme.primary else layers.raised,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    selected = selected,
                    shape = ControlShape,
                    border = layers.cardBorderStroke?.takeIf { !selected },
                    contentPadding = PaddingValues(0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.width(keyWidth).height(SheetKeyHeight),
                ) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The far jump, typed: 「跳到第 [4] / 5 页 ⇅ →」 as one outlined pill.
 *
 * Committed by the keyboard's own 前往 key as well as by the arrow: the keyboard is over the sheet the
 * whole time this has focus, and the arrow is where the eye already is.
 *
 * [floorsOffered] adds the ⇅ chip that switches the number between a page and a floor. A floor is
 * handed to [onGoToFloor] as typed; a page is clamped to the list's own range first.
 */
@Composable
private fun PageNumberField(
    page: Int,
    lastPage: Int,
    floorsOffered: Boolean,
    onGo: (Int) -> Unit,
    onGoToFloor: ((Int) -> Unit)?,
) {
    var unit by rememberSaveable { mutableStateOf(NumberEntry.Page) }
    var input by rememberSaveable { mutableStateOf("") }
    val floor = unit == NumberEntry.Floor && floorsOffered
    val number = input.toIntOrNull()
    val go = {
        when {
            number == null -> Unit
            floor -> onGoToFloor?.invoke(number.coerceAtLeast(0)) ?: Unit
            else -> onGo(number.coerceIn(1, lastPage))
        }
    }
    val layers = LocalPlazaLayers.current
    val fieldLabel =
        if (floor) {
            stringResource(Res.string.page_jump_floor_input, lastPage * COMMENTS_PER_PAGE)
        } else {
            stringResource(Res.string.page_jump_input, lastPage)
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(CircleShape)
            .background(layers.raised)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(start = 20.dp, end = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.page_jump_to),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A bare field inside a drawn pill rather than an OutlinedTextField: the design's field is
        // the whole row — prefix, number, suffix, unit chip and go button inside one outline — and
        // Material's field has one outline per field, around the input alone.
        BasicTextField(
            value = input,
            onValueChange = { typed -> input = typed.filter { it.isDigit() }.take(MAX_DIGITS) },
            singleLine = true,
            textStyle = FieldNumberStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            // As wide as what is typed, so the total can follow the number the way 9d draws it; a
            // single-line field otherwise takes every pixel it is offered.
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .widthIn(min = 12.dp)
                .semantics { contentDescription = fieldLabel },
            decorationBox = { field ->
                Box {
                    // The page the reader is on, greyed, until they type: says what the field
                    // takes without a label the row has no room for.
                    if (input.isEmpty()) {
                        Text(
                            if (floor) "#" else page.toString(),
                            style = FieldNumberStyle,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    field()
                }
            },
        )
        Text(
            stringResource(
                Res.string.page_jump_of_total,
                if (floor) lastPage * COMMENTS_PER_PAGE else lastPage,
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (floorsOffered) {
            UnitChip(
                floor = floor,
                onClick = {
                    unit = if (floor) NumberEntry.Page else NumberEntry.Floor
                    input = ""
                },
            )
        }
        FilledIconButton(
            onClick = go,
            enabled = number != null,
            modifier = Modifier.size(GoButtonSize),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(Res.string.page_jump_go),
            )
        }
    }
}

/** 页 ⇅ / 楼 ⇅ — the unit the typed number is in, and the tap that swaps it. */
@Composable
private fun UnitChip(
    floor: Boolean,
    onClick: () -> Unit,
) {
    val switchLabel = stringResource(Res.string.page_jump_switch_unit)
    val unitLabel = stringResource(if (floor) Res.string.page_jump_unit_floor else Res.string.page_jump_unit_page)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .height(GoButtonSize)
            .semantics {
                // Surface's click overload sets no role, so it is stated here.
                role = Role.Button
                contentDescription = switchLabel
                stateDescription = unitLabel
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(unitLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Icon(PlazaIcons.SwapVert, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * One destination tile: 上次阅读 / 第 3 页 · #41.
 *
 * Not a Material chip: the design's tile is two lines and a 24dp icon, sharing a row with its sibling
 * half and half.
 */
@Composable
private fun JumpTile(
    destination: JumpDestination,
    modifier: Modifier = Modifier,
) {
    TonalTile(
        onClick = destination.onGo,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        modifier = modifier.heightIn(min = JumpTileMinHeight),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(destination.icon, contentDescription = null)
            Column {
                Text(
                    destination.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                destination.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val KEYS_IN_VIEW = 5
private const val MAX_DIGITS = 6
private val SheetPadding = 16.dp
private val SheetKeyHeight = 48.dp
private val FieldHeight = 60.dp
private val GoButtonSize = 44.dp
private val JumpTileMinHeight = 56.dp

private val FieldNumberStyle =
    TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )
