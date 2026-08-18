package io.github.nodyssey.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
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
 * The jump sheet: a page number, and the destinations worth one tap.
 *
 * [progress] is the caller's own sentence because only the caller knows what it has loaded — the
 * thread counts 楼, the moderation log counts 条 — and a shared component inventing a noun for both
 * would be wrong in one of them.
 *
 * [resumePage] is where "上次阅读" goes, and a null one takes the button away with it. It is also
 * printed on the button, unlike the other two: 第一页 and 最后一页 say where they go in their own
 * names, and this one is a number only the app knows — a reader deciding whether to take the offer
 * is deciding about that number.
 *
 * Every destination here is only worth a button while it is somewhere else: 第一页 on page 1, or a
 * resume offer pointing at the page under the reader's thumb, are taps that do nothing and read as a
 * broken control rather than as a satisfied one. The caller decides what "上次阅读" means — the
 * thread remembers it across visits, the moderation log only knows how far this session scrolled —
 * but not whether it is worth showing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PageJumpSheet(
    page: Int,
    totalPages: Int,
    progress: String,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
    resumePage: Int? = null,
    onResume: () -> Unit = { resumePage?.let(onGo) },
) {
    var input by rememberSaveable { mutableStateOf(page.toString()) }
    val lastPage = totalPages.coerceAtLeast(1)
    // What the button both says and does. Clamping here rather than in each caller is what keeps
    // those two from disagreeing: the label used to promise page 9999 and the tap delivered the last
    // page. A blank field means "where I already am", which is also what the label falls back to —
    // it used to fall back in the label alone, leaving a button that read fine and did nothing.
    val target = input.toIntOrNull()?.coerceIn(1, lastPage) ?: page.coerceIn(1, lastPage)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Title and progress share a baseline, as the design draws them: the sentence is about
            // the heading rather than about the field under it. It keeps its own line when the two
            // no longer fit — a large font scale takes "第 2 / 12 页 · 已载入 28 / 165 楼" past any
            // room a three-character title leaves.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.page_jump_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    progress,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { character -> character.isDigit() } },
                label = { Text(stringResource(R.string.page_jump_input, lastPage)) },
                singleLine = true,
                keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                // The keyboard covers the button it would otherwise take two taps to reach.
                keyboardActions = KeyboardActions(onGo = { onGo(target) }),
                modifier = Modifier.fillMaxWidth(),
            )
            val showFirst = page > 1
            val resume = resumePage?.takeIf { it != page }
            val showLast = page < lastPage
            if (showFirst || resume != null || showLast) {
                // FlowRow, not Row: 第一页 and 最后一页 say where they go in their own names, but
                // "上次阅读" is the one destination whose page the reader cannot guess, so it carries
                // the number — and three chips, one of them that wide, do not fit a narrow phone in
                // one line, let alone at the 1.5× font scale 设置 offers.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showFirst) {
                        TextButton(onClick = { onGo(1) }) { Text(stringResource(R.string.page_jump_first)) }
                    }
                    if (resume != null) {
                        TextButton(onClick = onResume) {
                            Text(stringResource(R.string.page_jump_latest_read, resume))
                        }
                    }
                    if (showLast) {
                        TextButton(onClick = { onGo(lastPage) }) { Text(stringResource(R.string.page_jump_last)) }
                    }
                }
            }
            Button(onClick = { onGo(target) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.page_jump_go, target.toString()))
            }
        }
    }
}
