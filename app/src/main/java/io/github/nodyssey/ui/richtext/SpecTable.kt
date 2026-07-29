package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

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
 * table exists for; a value too long for its column is a column that needs to be wider.
 */
@Composable
internal fun SpecTable(
    columns: List<String>,
    rows: List<SpecRow>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val cells = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            SpecHeaderCell(text = "", width = LABEL_WIDTH)
            rows.forEach { row ->
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .width(LABEL_WIDTH)
                        .padding(horizontal = Spacing.sm, vertical = 5.dp),
                )
            }
        }
        Column(modifier = Modifier.horizontalScroll(cells)) {
            Row { columns.forEach { SpecHeaderCell(it) } }
            rows.forEach { row ->
                Row {
                    // Padded to the header's length: a row that is short is short at a known column,
                    // and a blank there is the finding.
                    List(columns.size) { row.cells.getOrElse(it) { "" } }.forEach { cell ->
                        Text(
                            text = cell,
                            style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = TABULAR_FIGURES,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier
                                .width(CELL_WIDTH)
                                .padding(horizontal = Spacing.sm, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One row: the pinned label, then one entry per column. Missing entries render blank. */
internal data class SpecRow(
    val label: String,
    val cells: List<String>,
)

/**
 * A grid whose first row is the header and whose first column is the row label.
 *
 * That is how the site's Markdown tables are written — the parser hands back a plain grid, and the
 * leading column is what identifies the row, same as in a report.
 */
internal fun List<List<String>>.asSpecTable(): Pair<List<String>, List<SpecRow>> {
    val header = firstOrNull().orEmpty()
    return header.drop(1) to drop(1).map { SpecRow(it.firstOrNull().orEmpty(), it.drop(1)) }
}

@Composable
private fun SpecHeaderCell(
    text: String,
    width: Dp = CELL_WIDTH,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .width(width)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
    )
}

/** Wide enough for `操作系统/内核`, which is the longest label the scripts use. */
private val LABEL_WIDTH = 92.dp

/** Wide enough for `IP2Location` at 13sp, which is the longest column name. */
private val CELL_WIDTH = 88.dp
