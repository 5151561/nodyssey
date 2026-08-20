package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_copy
import io.github.nodyssey.ui.resources.post_code_copied
import io.github.nodyssey.ui.resources.report_open_fullscreen
import io.github.plaza.core.ansi.AnsiSpan
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.TerminalGround
import io.github.plaza.designsys.component.TerminalInk
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberTerminalText
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The report exactly as it was posted, on a terminal ground, pinchable.
 *
 * This is the escape hatch behind every [ReportCard]: the scripts are versioned and the card is an
 * interpretation, so whatever it gets wrong has to be checkable against the original. It is also the
 * only place the eighty-column layout is honoured, which is why it is full screen and why the type
 * starts at whatever size makes the widest line fit rather than at a fixed one.
 *
 * The ground stays dark in both themes. The reports colour their verdicts by background — a green
 * `解锁`, a red `机房` — and those stop meaning anything on a light surface.
 *
 * A dialog rather than a navigation destination: the report is several kilobytes of text, and a
 * back-stack entry is serialised into the saved instance state.
 */
@Composable
fun ReportSourceDialog(
    title: String,
    source: String,
    spans: List<AnsiSpan>,
    columns: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var zoom by remember(source) { mutableFloatStateOf(1f) }
        val copy = rememberClipboardCopy()
        val confirmation = stringResource(Res.string.post_code_copied)
        val coloured = rememberTerminalText(source, spans)

        // The dialog is its own window, and so its own layout root, but it is composed from inside the
        // post's `SelectionContainer` and would inherit that container's `LocalSelectionRegistrar`.
        // The text here would then register as selectable with a manager whose container lives in the
        // other hierarchy, and the long press that a slow pinch always produces would ask it to map
        // one root's coordinates into the other's: `layouts are not part of the same hierarchy`, and
        // the app is gone. Severing the registrar is also what the pinch wants — selection handles
        // fighting a two-finger zoom is not a gesture anyone can win — and the whole report is one
        // button away in the clipboard regardless.
        DisableSelection {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TerminalGround),
            ) {
                Column(Modifier.systemBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.lg, end = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TerminalInk,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { copy("report", source, confirmation) }) {
                            Icon(
                                imageVector = PlazaIcons.ContentCopy,
                                contentDescription = stringResource(Res.string.action_copy),
                                tint = TerminalInk,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_close),
                                tint = TerminalInk,
                            )
                        }
                    }

                    BoxWithFit(columns = columns, zoom = zoom, onZoom = { zoom = it }) { fontScale ->
                        Text(
                            text = coloured,
                            style = ReportTerminalStyle.scaledBy(fontScale),
                            color = TerminalInk,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The same report, on the same ground, but inline in the floor — what 测评报告 = 原文 draws instead
 * of [ReportCard].
 *
 * It is the [ReportSourceDialog]'s layout minus the parts a post body cannot have: no vertical
 * scroller, because a floor is already inside one and the whole report is meant to scroll past with
 * the rest of the post; and no cropping, because a setting that says 原文 must not quietly show a
 * fraction of it. The type still starts at the size that makes eighty columns fit rather than at a
 * readable one — that is what keeps the report's own alignment, its bars and its boxes, intact — so
 * the pinch and 全屏查看 are how it actually gets read.
 *
 * The colours come with it. 原文 means the output as the script wrote it, and the script wrote half
 * its meaning in ANSI — a verdict is a green or a red chip, not a word — so a monochrome 原文 is a
 * different report, not a plainer one.
 */
@Composable
fun ReportSourceBlock(
    title: String,
    source: String,
    spans: List<AnsiSpan>,
    columns: Int,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by rememberSaveable(source) { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { _, zoomChange, _, _ ->
        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }
    val copy = rememberClipboardCopy()
    val confirmation = stringResource(Res.string.post_code_copied)
    val coloured = rememberTerminalText(source, spans)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(TerminalGround),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TerminalInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { copy("report", source, confirmation) }) {
                Icon(
                    imageVector = PlazaIcons.ContentCopy,
                    contentDescription = stringResource(Res.string.action_copy),
                    tint = TerminalInk,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // `canPan = { false }` for the reason [BoxWithFit] gives, and it matters more here:
                // a one-finger drag inside a floor belongs to the thread's list, not to this block.
                .transformable(state = transform, canPan = { false }),
        ) {
            val fitted = fittedScale(maxWidth - Spacing.md * 2, columns)
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Text(
                    text = coloured,
                    style = ReportTerminalStyle.scaledBy(fitted * zoom),
                    color = TerminalInk,
                    softWrap = false,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .defaultMinSize(minHeight = Sizes.minTouchTarget)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.report_open_fullscreen),
                style = MaterialTheme.typography.labelLarge,
                color = TerminalInk,
            )
        }
    }
}

/**
 * Scrolls both ways and starts at the scale that makes [columns] fit across the screen.
 *
 * The zoom changes the *type size* rather than transforming a layer, because the content has to stay
 * scrollable to its new edges — a scaled layer keeps the size it was laid out at, so half the report
 * would end up somewhere the scroll could not reach.
 *
 * Only a second finger drives it. `canPan = { false }` is exactly that rule: a one-finger drag is
 * never consumed here, so it reaches the scroll containers below and the report stays scrollable the
 * moment it is readable.
 */
@Composable
private fun BoxWithFit(
    columns: Int,
    zoom: Float,
    onZoom: (Float) -> Unit,
    content: @Composable (Float) -> Unit,
) {
    // `TransformableState` accumulates the gesture itself and `rememberTransformableState` keeps the
    // callback current, so the multiplication always reads today's [zoom]. The hand-rolled detector
    // this replaces captured `zoom` by value inside `pointerInput(Unit)`, whose coroutine never
    // restarts — it multiplied every event against the initial 1f, and since calculateZoom returns a
    // per-event delta of a percent or two, the pinch could never actually reach a larger size.
    val transform = rememberTransformableState { _, zoomChange, _, _ ->
        onZoom((zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM))
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transform, canPan = { false }),
    ) {
        val fitted = fittedScale(maxWidth - Spacing.lg * 2, columns)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            content(fitted * zoom)
        }
    }
}

/**
 * The type size, as a fraction of [ReportTerminalStyle]'s, at which [columns] just fit across
 * [available].
 *
 * The advance of a monospace glyph is about six tenths of its size, which is the whole of the
 * arithmetic. It is deliberately allowed to come out tiny: the report at a glance is what the fit is
 * for, and reading it is what the pinch is for.
 */
private fun fittedScale(available: Dp, columns: Int): Float =
    if (columns <= 0) {
        1f
    } else {
        (available.value / columns / MONOSPACE_ADVANCE / BASE_SIZE).coerceIn(MIN_FIT, 1f)
    }

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

private const val MONOSPACE_ADVANCE = 0.6f
private const val BASE_SIZE = 12f
private const val MIN_FIT = 0.4f
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 6f
