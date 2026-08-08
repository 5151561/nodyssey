package io.github.nodyssey.ui.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
import io.github.nodyssey.core.report.QualityReport
import io.github.nodyssey.ui.theme.CodeStyle
import io.github.nodyssey.ui.theme.LocalNodysseyExtraColors
import io.github.nodyssey.ui.theme.ReportData
import io.github.nodyssey.ui.theme.ReportLabel
import io.github.nodyssey.ui.theme.Sizes
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

/**
 * A NodeQuality benchmark report, rebuilt out of ordinary rows instead of the terminal art it
 * arrived as.
 *
 * The report is eighty columns wide and cannot become narrower without becoming unreadable: fitting
 * eighty columns across a phone puts the type near 7sp. So the layout is not reproduced at all. What
 * the padding encoded — this is a label, that is its value, these five belong to one comparison — is
 * recovered by [io.github.nodyssey.core.report.QualityReportParser] and drawn again at [ReportData]'s
 * 13sp, where a value that no longer fits wraps instead of scrolling off the side.
 *
 * The scripts are versioned and their layout moves, so [onShowSource] stays available on every card:
 * whatever this misreads, the original is one tap away and is still the thing that was posted.
 */
@Composable
fun ReportCard(
    report: QualityReport,
    onShowSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(report.title, report.target) { mutableStateOf(true) }

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ReportHeader(
            report = report,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.md,
                        bottom = Spacing.sm,
                    ),
                    // md rather than lg: the sections' own type dropped to 13sp, and the old gap
                    // read as a break between cards rather than between sections of one card.
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    report.sections.forEach { ReportSection(it) }
                    if (report.footnotes.isNotEmpty()) Footnotes(report.footnotes)
                }
                SourceAction(onShowSource)
            }
        }
    }
}

@Composable
private fun ReportHeader(
    report: QualityReport,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .defaultMinSize(minHeight = Sizes.minTouchTarget)
            .padding(start = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                report.target?.let { target ->
                    Text(
                        text = target,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            // The timestamp is the one piece of provenance that matters: a benchmark from six months
            // ago describes a machine that has since been resold.
            listOfNotNull(report.generatedAt, report.scriptVersion)
                .takeIf { it.isNotEmpty() }
                ?.let { meta ->
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
        Box(
            modifier = Modifier.size(Sizes.minTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            // The chevron turns rather than flipping: the card's own expansion is animated, and an
            // indicator that snaps while the thing it points at slides reads as two separate events.
            val chevronAngle by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "chevron",
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription =
                stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronAngle),
            )
        }
    }
}

@Composable
private fun ReportSection(section: QualityReport.Section) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (section.title.isNotBlank()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        section.blocks.forEach { block ->
            when (block) {
                is QualityReport.Block.Field -> FieldRow(block)
                is QualityReport.Block.Badges -> BadgeRow(block)
                is QualityReport.Block.Table -> ReportTable(block)
                is QualityReport.Block.Note -> NoteLine(block)
            }
        }
    }
}

/**
 * A line the parser could not take apart, kept exactly as the terminal drew it.
 *
 * Monospace and scrolling rather than wrapping, because whatever structure such a line still has is
 * in its spacing — NetQuality's latency bars are drawn out of block characters, and reflowing them
 * into the body face turns a chart into noise. This is the seam where the card degrades, and it
 * degrades into the original rather than into a mess.
 */
@Composable
private fun NoteLine(note: QualityReport.Block.Note) {
    Text(
        text = note.text,
        style = CodeStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    )
}

/**
 * `标签  值`, with the label in a column of its own.
 *
 * A fixed label column rather than an inline `标签：值` run so that a reader scanning for one fact
 * has a single left edge to run their eye down. It is a minimum rather than a fixed width: a long
 * label takes the room it needs and the value wraps under it.
 */
@Composable
private fun FieldRow(field: QualityReport.Block.Field) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = field.label,
            style = ReportLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(min = LABEL_WIDTH)
                .padding(end = Spacing.sm),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            field.values.forEach { value ->
                Text(
                    text = value,
                    style = ReportData.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRow(badges: QualityReport.Block.Badges) {
    val extra = LocalNodysseyExtraColors.current

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = badges.label,
            style = ReportLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(min = LABEL_WIDTH)
                .padding(end = Spacing.sm),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            badges.items.forEach { badge ->
                Text(
                    text = badge.text,
                    // One step under [ReportData] with a little weight back: a chip's fill already
                    // carries the verdict, the text only has to stay legible.
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color =
                    if (badge.passed) extra.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (badge.passed) {
                                extra.successContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * A comparison grid, with its row labels held still while the cells scroll.
 *
 * 风险因子 asks eight databases the same seven questions, and the answer only means something next
 * to the question — so the label column cannot be allowed to scroll away from the cells the way it
 * would in a plain horizontally-scrolled table.
 */
@Composable
private fun ReportTable(table: QualityReport.Block.Table) {
    SpecTable(
        columns = table.columns.map(::AnnotatedString),
        rows = table.rows.map { SpecRow(label = it.label, cells = it.cells) },
        labelMinWidth = LABEL_WIDTH,
    )
}

@Composable
private fun Footnotes(footnotes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        footnotes.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceAction(onShowSource: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowSource)
            .defaultMinSize(minHeight = Sizes.minTouchTarget)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.report_show_source),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

internal val ReportTerminalGround = Color(0xFF282C34)

internal val ReportTerminalInk = Color(0xFFD7DAE0)

internal val ReportTerminalStyle =
    androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

/**
 * The width a field row reserves for its label before the value starts.
 *
 * Also handed to [SpecTable] as its pinned column's minimum — a card reads as one grid whether the
 * block is a field list or a table, so the two label edges share one number.
 */
private val LABEL_WIDTH = 92.dp
