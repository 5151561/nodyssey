package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nodyssey.R
import io.github.nodyssey.model.AnsiSpan
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.rememberClipboardCopy
import io.github.nodyssey.ui.theme.Spacing

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
        val confirmation = stringResource(R.string.post_code_copied)
        val coloured = rememberTerminalText(source, spans)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReportTerminalGround),
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
                        color = ReportTerminalInk,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { copy("report", source, confirmation) }) {
                        Icon(
                            imageVector = NodysseyIcons.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy),
                            tint = ReportTerminalInk,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = ReportTerminalInk,
                        )
                    }
                }

                BoxWithFit(columns = columns, zoom = zoom, onZoom = { zoom = it }) { fontScale ->
                    Text(
                        text = coloured,
                        style = ReportTerminalStyle.copy(
                            fontSize = ReportTerminalStyle.fontSize * fontScale,
                            lineHeight = ReportTerminalStyle.lineHeight * fontScale,
                        ),
                        color = ReportTerminalInk,
                        softWrap = false,
                    )
                }
            }
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
    val transform = rememberTransformableState { zoomChange, _, _ ->
        onZoom((zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM))
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transform, canPan = { false }),
    ) {
        val available = maxWidth - Spacing.lg * 2
        // The advance of a monospace glyph is about six tenths of its size, so this is the size at
        // which the widest line just fits. It is deliberately allowed to be tiny: the whole report
        // at a glance is what the fit is for, and reading it is what the pinch is for.
        val fitted = remember(available, columns) {
            if (columns <= 0) 1f else (available.value / columns / MONOSPACE_ADVANCE / BASE_SIZE).coerceIn(MIN_FIT, 1f)
        }

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

private const val MONOSPACE_ADVANCE = 0.6f
private const val BASE_SIZE = 12f
private const val MIN_FIT = 0.4f
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 6f
