package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

/**
 * The one table in this app.
 *
 * Material 3 has no data table, so this is filling a gap rather than replacing a component — but it
 * had been filled twice, once for report cards and once for tables in a post body, and the two copies
 * had already drifted apart on column width, header container colour and whether a cell may wrap.
 * Both surfaces render the same kind of thing: benchmark output, wider than the screen, read by
 * comparing one row against another.
 *
 * The first column is pinned and the rest scroll under it. That is what makes a wide table readable
 * on a phone — scroll to the far column and you can still see which row you are on.
 *
 * Cells never wrap. A wrapped cell makes its whole row taller, which breaks the horizontal scan the
 * table exists for; a value too long for its column is a column that needs to be wider — so each
 * column takes the width of its widest cell. Two limits keep that honest: a column is capped at a
 * fraction of the table's width so one prose cell cannot push every other column off screen, and the
 * pinned column's cap plus the cell cap sum to less than the whole, so the pinned label and at least
 * one full cell are always on screen together. The ellipsis, which used to mark any cell longer than
 * a fixed 88dp, now appears only when a cell hits its cap.
 *
 * A table narrower than its container is stretched to fill it, each column growing in proportion —
 * three short columns read as a table, not as a strip huddled against the left edge.
 */
@Composable
fun SpecTable(
    columns: List<AnnotatedString>,
    rows: List<SpecRow>,
    modifier: Modifier = Modifier,
    /**
     * The pinned column never sizes below this. A report card passes the width its field rows
     * reserve for their labels, so a card's field list and its tables share one label edge.
     */
    labelMinWidth: Dp = MIN_COLUMN_WIDTH,
) {
    if (rows.isEmpty()) return
    val cells = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.bodySmall
    val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val cellStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val available = maxWidth
        val widths =
            remember(columns, rows, labelMinWidth, available, density, labelStyle, headerStyle, cellStyle) {
                columnWidths(
                    columns = columns,
                    rows = rows,
                    labelMinWidth = labelMinWidth,
                    available = available,
                    density = density,
                    measurer = measurer,
                    labelStyle = labelStyle,
                    headerStyle = headerStyle,
                    cellStyle = cellStyle,
                )
            }
        Row {
            Column {
                SpecHeaderCell(text = AnnotatedString(""), width = widths.label)
                rows.forEach { row ->
                    Text(
                        text = row.label,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(widths.label)
                            .padding(horizontal = Spacing.sm, vertical = 5.dp),
                    )
                }
            }
            Column(modifier = Modifier.horizontalScroll(cells)) {
                Row {
                    columns.forEachIndexed { index, column ->
                        SpecHeaderCell(text = column, width = widths.cells[index])
                    }
                }
                rows.forEach { row ->
                    Row {
                        // Padded to the header's length: a row that is short is short at a known column,
                        // and a blank there is the finding.
                        List(columns.size) { row.cells.getOrElse(it) { AnnotatedString("") } }
                            .forEachIndexed { index, cell ->
                                Text(
                                    text = cell,
                                    style = cellStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    // Only a capped cell has anything to cut, and clipping it mid-glyph
                                    // reads as a rendering fault rather than as "there is more here".
                                    // The ellipsis is the only thing that says which.
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(widths.cells[index])
                                        .padding(horizontal = Spacing.sm, vertical = 5.dp),
                                )
                            }
                    }
                }
            }
        }
    }
}

/**
 * One row: the pinned label, then one entry per column. Missing entries render blank.
 *
 * Cells are annotated rather than plain, because both surfaces need more than characters out of a
 * cell: a post body's cell can carry a link, and the annotation is what keeps it tappable, while a
 * report's cell carries the colour its verdict was written in.
 */
data class SpecRow(
    val label: AnnotatedString,
    val cells: List<AnnotatedString>,
)

/**
 * A grid whose first row is the header and whose first column is the row label.
 *
 * That is how the site's Markdown tables are written — the parser hands back a plain grid, and the
 * leading column is what identifies the row, same as in a report.
 */
fun List<List<AnnotatedString>>.asSpecTable(): Pair<List<AnnotatedString>, List<SpecRow>> {
    val header = firstOrNull().orEmpty()
    return header.drop(1) to
        drop(1).map { SpecRow(it.firstOrNull() ?: AnnotatedString(""), it.drop(1)) }
}

private class ColumnWidths(
    val label: Dp,
    val cells: List<Dp>,
)

/**
 * Each column at its widest cell, bounded, then stretched to the container.
 *
 * Bounds are applied cap-last: on a container too narrow to honour both, a column keeps to its cap
 * and gives up its floor, because the cap is what keeps the neighbouring columns reachable.
 *
 * The stretch is allowed to carry a capped column back past its cap. The cap defends a table that
 * scrolls — one cell must not put the others a screen away — but a table with room to spare has no
 * one off screen to defend, and the spare room does the most good in exactly the column that was
 * cut short.
 */
private fun columnWidths(
    columns: List<AnnotatedString>,
    rows: List<SpecRow>,
    labelMinWidth: Dp,
    available: Dp,
    density: Density,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    headerStyle: TextStyle,
    cellStyle: TextStyle,
): ColumnWidths {
    fun intrinsic(
        text: AnnotatedString,
        style: TextStyle,
    ): Dp = with(density) {
        measurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toDp()
    }

    val padding = Spacing.sm * 2
    val label =
        (rows.maxOf { intrinsic(it.label, labelStyle) } + padding)
            .coerceAtLeast(labelMinWidth)
            .coerceAtMost(available * MAX_LABEL_FRACTION)
    val cells =
        List(columns.size) { index ->
            val widest =
                rows.fold(intrinsic(columns[index], headerStyle)) { acc, row ->
                    val cell = row.cells.getOrNull(index) ?: return@fold acc
                    maxOf(acc, intrinsic(cell, cellStyle))
                }
            (widest + padding)
                .coerceAtLeast(MIN_COLUMN_WIDTH)
                .coerceAtMost(available * MAX_CELL_FRACTION)
        }
    val sum = cells.fold(0.dp, Dp::plus)
    val slack = available - label - sum
    if (slack <= 0.dp || sum <= 0.dp) return ColumnWidths(label, cells)
    return ColumnWidths(label, cells.map { it + slack * (it.value / sum.value) })
}

@Composable
private fun SpecHeaderCell(
    text: AnnotatedString,
    width: Dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
    )
}

/** The floor: a column of blanks still has to be visibly a column. */
private val MIN_COLUMN_WIDTH = 48.dp

/**
 * Caps, as fractions of the table's width. They sum below 1 so the pinned label and one whole cell
 * always fit on screen at once — the point of pinning is lost if the label alone can fill the view.
 */
private const val MAX_LABEL_FRACTION = 0.4f

private const val MAX_CELL_FRACTION = 0.6f
