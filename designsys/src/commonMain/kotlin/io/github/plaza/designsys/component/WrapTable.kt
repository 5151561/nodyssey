package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.plaza.designsys.image.ImagesDeferredException
import io.github.plaza.designsys.image.allowMeteredImage
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.richtext_action_retry
import io.github.plaza.designsys.resources.richtext_image_load_failed
import io.github.plaza.designsys.resources.richtext_image_skipped_action
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/**
 * The wrapping table: for grids read cell by cell, whole on screen at once.
 *
 * [SpecTable]'s counterpart. That table exists for numeric grids scanned row against row, and it
 * buys the scan with two hard rules — one line per cell, columns capped — that turn prose into
 * ellipses and put far columns a swipe away. This one makes the opposite trade, the same one the
 * forum's own stylesheet makes with `max-width: 100%` and `word-break: break-word`: the table
 * always fits the screen, every cell is shown whole, and a cell too wide for its column wraps
 * downward instead of being cut. Gridlines carry the structure that uniform row heights no longer
 * can.
 *
 * Column widths follow the shape of CSS automatic table layout: each column asks for the width of
 * its widest single-line cell, and when the screen cannot honour every request the space is
 * divided in proportion to what was asked, with a floor so no column is squeezed out entirely. A
 * table narrower than its container is stretched to fill it, for [SpecTable]'s reason: three short
 * columns should read as a table, not as a strip against the left edge.
 *
 * Cells can hold images — the site's posts use layout tables for exactly that, a 2×2 grid of
 * result screenshots — and those draw as fixed-height thumbnails that open the full image.
 */
@Composable
fun WrapTable(
    /** Cells in reading order, header row first, rows free to run short. */
    rows: List<List<WrapCell>>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val columnCount = rows.maxOf { it.size }
    if (columnCount == 0) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val cellStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES)
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val available = maxWidth - DividerDefaults.Thickness * (columnCount - 1)
        val widths =
            remember(rows, columnCount, available, density, headerStyle, cellStyle) {
                wrapColumnWidths(
                    rows = rows,
                    columnCount = columnCount,
                    available = available,
                    density = density,
                    measurer = measurer,
                    headerStyle = headerStyle,
                    cellStyle = cellStyle,
                )
            }
        Column {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) HorizontalDivider(color = lineColor)
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(
                            if (rowIndex == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        ),
                ) {
                    repeat(columnCount) { columnIndex ->
                        if (columnIndex > 0) {
                            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = lineColor)
                        }
                        CellContent(
                            // Padded to the widest row's length: a row that is short is short at a
                            // known column, and a blank there is the finding.
                            cell = row.getOrNull(columnIndex),
                            header = rowIndex == 0,
                            style = if (rowIndex == 0) headerStyle else cellStyle,
                            width = widths[columnIndex],
                            onImageClick = onImageClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One cell: text that may wrap, and any images the cell carried, drawn under it.
 *
 * Text and images are separate fields rather than an interleaved list because a cell is not a
 * paragraph — the site's own cells hold either a value or a screenshot, and the one post in a
 * thousand that mixes them loses only the interleaving order, not the content.
 */
data class WrapCell(
    val text: AnnotatedString,
    val images: List<WrapCellImage> = emptyList(),
)

data class WrapCellImage(
    val url: String,
    val alt: String?,
)

@Composable
private fun CellContent(
    cell: WrapCell?,
    header: Boolean,
    style: TextStyle,
    width: Dp,
    onImageClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = Spacing.sm, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (cell != null && cell.text.isNotEmpty()) {
            // A 拼车 post files its benchmark reports in a table column, so this cell is where most
            // of the outbound links in a thread actually are — and the one place worth telling the
            // browser about a link while the finger is still on it. See `prefetchLinksOnPress`.
            var layout by remember(cell.text) { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = cell.text,
                style = style,
                color =
                if (header) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                onTextLayout = { layout = it },
                modifier = Modifier.prefetchLinksOnPress(cell.text) { layout },
            )
        }
        cell?.images?.forEach { image ->
            CellThumbnail(image = image, onImageClick = onImageClick)
        }
    }
}

/**
 * A cell's image at thumbnail height, whole (never cropped), a tap away from full size.
 *
 * Fixed height rather than intrinsic: the row's height is measured before the image loads, and an
 * image that grew on arrival would shove the rows below it mid-read.
 *
 * Honours 仅 Wi-Fi 加载图片 the same way the full-size image does — skipped rather than fetched,
 * with a tap loading this one image anyway. A plain load failure says so and offers a retry: an
 * empty frame in a grid of screenshots does not read as "one failed", it reads as "the grid is
 * 1×2" — a cell must never fail invisibly.
 */
@Composable
private fun CellThumbnail(
    image: WrapCellImage,
    onImageClick: (String) -> Unit,
) {
    var allowMetered by remember(image.url) { mutableStateOf(false) }
    var retryToken by remember(image.url) { mutableIntStateOf(0) }
    var failure by remember(image.url, allowMetered, retryToken) {
        mutableStateOf<CellImageFailure?>(null)
    }
    val context = LocalPlatformContext.current
    val request =
        remember(image.url, allowMetered, retryToken) {
            ImageRequest
                .Builder(context)
                .data(image.url)
                .allowMeteredImage(allowMetered)
                .build()
        }
    when (failure) {
        CellImageFailure.Deferred -> {
            ThumbnailNotice(
                text = stringResource(Res.string.richtext_image_skipped_action),
                onClick = { allowMetered = true },
            )
            return
        }

        CellImageFailure.Failed -> {
            ThumbnailNotice(
                text =
                stringResource(Res.string.richtext_image_load_failed) + "\n" +
                    stringResource(Res.string.richtext_action_retry),
                onClick = { retryToken++ },
            )
            return
        }

        null -> Unit
    }
    key(request) {
        AsyncImage(
            model = request,
            contentDescription = image.alt,
            contentScale = ContentScale.Fit,
            onError = { error ->
                failure = if (error.result.throwable is ImagesDeferredException) {
                    CellImageFailure.Deferred
                } else {
                    CellImageFailure.Failed
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(THUMBNAIL_HEIGHT)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { onImageClick(image.url) },
        )
    }
}

private enum class CellImageFailure { Deferred, Failed }

/** The thumbnail frame with words in it: what happened, and that a tap acts on it. */
@Composable
private fun ThumbnailNotice(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(THUMBNAIL_HEIGHT)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Each column at the width of its widest single-line cell, then fitted to the container: stretched
 * in proportion when there is room, shrunk in proportion when there is not.
 *
 * The proportional shrink is the piece that stands in for the browser's automatic table layout —
 * a column that asked for three times the width keeps three times the width, so the squeeze reads
 * as the same table drawn narrower rather than as a different table. The floor is applied
 * column-by-column: a column pushed below it is pinned at the floor and the remainder is divided
 * among the rest, until every column either holds its proportion or sits at the floor.
 */
private fun wrapColumnWidths(
    rows: List<List<WrapCell>>,
    columnCount: Int,
    available: Dp,
    density: Density,
    measurer: TextMeasurer,
    headerStyle: TextStyle,
    cellStyle: TextStyle,
): List<Dp> {
    fun intrinsic(
        text: AnnotatedString,
        style: TextStyle,
    ): Dp = with(density) {
        measurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toDp()
    }

    val padding = Spacing.sm * 2
    val preferred =
        List(columnCount) { column ->
            var widest = 0.dp
            rows.forEachIndexed { rowIndex, row ->
                val cell = row.getOrNull(column) ?: return@forEachIndexed
                val style = if (rowIndex == 0) headerStyle else cellStyle
                var width = if (cell.text.isEmpty()) 0.dp else intrinsic(cell.text, style)
                // An image is happy at any width, so it asks only for enough to stay legible.
                if (cell.images.isNotEmpty()) width = maxOf(width, IMAGE_PREFERRED_WIDTH)
                widest = maxOf(widest, width)
            }
            widest + padding
        }
    val sum = preferred.fold(0.dp, Dp::plus)
    if (sum <= 0.dp) return List(columnCount) { available / columnCount }
    if (sum <= available) {
        val slack = available - sum
        return preferred.map { it + slack * (it.value / sum.value) }
    }
    // The guard keeps the floors themselves from overflowing a very narrow container.
    val floor = minOf(MIN_WRAP_COLUMN_WIDTH, available / columnCount)
    val widths = MutableList(columnCount) { 0.dp }
    val flexible = (0 until columnCount).toMutableList()
    var budget = available
    while (flexible.isNotEmpty()) {
        val flexSum = flexible.fold(0f) { acc, column -> acc + preferred[column].value }
        val pinned = flexible.filter { column -> budget * (preferred[column].value / flexSum) < floor }
        if (pinned.isEmpty()) {
            flexible.forEach { column -> widths[column] = budget * (preferred[column].value / flexSum) }
            break
        }
        pinned.forEach { column ->
            widths[column] = floor
            budget -= floor
            flexible.remove(column)
        }
    }
    return widths
}

/** Below this a text column is unreadable — one or two hanzi per line — whatever it asked for. */
private val MIN_WRAP_COLUMN_WIDTH = 48.dp

/** What an image cell asks its column for; images scale, so this is legibility, not a demand. */
private val IMAGE_PREFERRED_WIDTH = 120.dp

private val THUMBNAIL_HEIGHT = 120.dp
