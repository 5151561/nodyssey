package io.github.nodyssey.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.report.QualityReport
import io.github.nodyssey.core.report.ReportComparison
import io.github.nodyssey.data.settings.ReportCompareEntry
import io.github.nodyssey.data.settings.ReportCompareStore
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.report_compare_basket
import io.github.nodyssey.ui.resources.report_compare_empty
import io.github.nodyssey.ui.resources.report_compare_missing
import io.github.nodyssey.ui.resources.report_compare_open_post
import io.github.nodyssey.ui.resources.report_compare_pick_hint
import io.github.nodyssey.ui.resources.report_compare_remove_entry
import io.github.nodyssey.ui.resources.report_compare_table
import io.github.nodyssey.ui.resources.report_compare_title
import io.github.nodyssey.ui.richtext.toneColour
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.ReportData
import io.github.plaza.designsys.theme.ReportLabel
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReportCompareRoute(
    viewModel: ReportCompareViewModel,
    onBack: () -> Unit,
    onOpenPost: (postId: Long, floor: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportCompareScreen(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onRemove = viewModel::remove,
        onOpenPost = onOpenPost,
        modifier = modifier,
    )
}

/**
 * 测评对比: the basket on top, the comparison of what is ticked below it.
 *
 * The comparison is drawn as rows rather than through `SpecTable` or `WrapTable`. Both size each
 * column to its content, and that is wrong here: the columns are the reports, and a reader comparing
 * three machines needs them the same width so that one report's long CPU name does not make it look
 * like the important one. Neither does a pinned label column fit three reports across 360dp, so the
 * label goes on a line of its own above its values, which then get the whole width between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCompareScreen(
    state: ReportCompareUiState,
    onBack: () -> Unit,
    onToggle: (ReportCompareEntry) -> Unit,
    onRemove: (ReportCompareEntry) -> Unit,
    onOpenPost: (postId: Long, floor: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.report_compare_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
            contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (!state.loaded) return@LazyColumn
            if (state.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(Res.string.report_compare_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                    )
                }
                return@LazyColumn
            }

            item(key = "basket-label") {
                SectionLabel(
                    stringResource(Res.string.report_compare_basket, state.entries.size, ReportCompareStore.MAX_ENTRIES),
                )
            }
            item(key = "basket") {
                Column {
                    state.entries.forEachIndexed { index, entry ->
                        val selected = state.isSelected(entry)
                        BasketRow(
                            entry = entry,
                            selected = selected,
                            enabled = selected || state.selected.size < ReportCompareViewModel.MAX_COMPARED,
                            first = index == 0,
                            last = index == state.entries.lastIndex,
                            onToggle = { onToggle(entry) },
                            onRemove = { onRemove(entry) },
                        )
                    }
                }
            }

            val comparison = state.comparison
            if (comparison == null) {
                item(key = "hint") {
                    MetaText(
                        stringResource(Res.string.report_compare_pick_hint),
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                return@LazyColumn
            }

            item(key = "table-label") { SectionLabel(stringResource(Res.string.report_compare_table)) }
            // Held at the top while the sections scroll under it: by the third section the reader
            // no longer remembers which column was which machine.
            stickyHeader(key = "columns") {
                ColumnHeaders(entries = state.selected, onOpenPost = onOpenPost)
            }
            itemsIndexed(comparison.sections, key = { index, _ -> "section-$index" }) { _, section ->
                ComparisonSection(section)
            }
        }
    }
}

@Composable
private fun BasketRow(
    entry: ReportCompareEntry,
    selected: Boolean,
    enabled: Boolean,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = entry.columnName()
    GroupedListItem(
        first = first,
        last = last,
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        enabled = enabled,
        leadingContent = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
        headlineContent = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { MetaText(entry.provenance(), singleLine = true) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.report_compare_remove_entry, name))
            }
        },
    )
}

/** One equal-width header per compared report; a report from a thread opens it at its floor. */
@Composable
private fun ColumnHeaders(
    entries: List<ReportCompareEntry>,
    onOpenPost: (postId: Long, floor: String?) -> Unit,
) {
    val openLabel = stringResource(Res.string.report_compare_open_post)
    Surface(color = LocalPlazaLayers.current.page, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            entries.forEach { entry ->
                val postId = entry.postId
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (postId != null) {
                                Modifier.clickable(onClickLabel = openLabel) { onOpenPost(postId, entry.floor) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = Spacing.sm),
                ) {
                    Text(
                        text = listOfNotNull(entry.threadTitle ?: entry.report.title, entry.floor).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (postId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(entry.report.title.takeIf { entry.threadTitle != null }, entry.report.target)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonSection(section: ReportComparison.Section) {
    LayerCard(contentPadding = PaddingValues(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (section.title.isNotBlank()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        section.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = LocalPlazaLayers.current.divider)
            ComparisonRow(row)
        }
    }
}

@Composable
private fun ComparisonRow(row: ReportComparison.Row) {
    val missing = stringResource(Res.string.report_compare_missing)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = row.label, style = ReportLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            row.cells.forEach { cell ->
                Column(modifier = Modifier.weight(1f)) {
                    if (cell == null) {
                        Text(
                            text = MISSING,
                            style = ReportData,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { contentDescription = missing },
                        )
                    } else {
                        cell.forEach { value ->
                            Text(
                                text = value.text,
                                style = ReportData.copy(fontFeatureSettings = TABULAR_FIGURES),
                                color = toneColour(value.tone),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What a basket row and a column are called: the thread, or the report itself when it had none. */
private fun ReportCompareEntry.columnName(): String = threadTitle ?: report.title

/** The line under a basket row's name: which report, from which floor, of which machine, when. */
private fun ReportCompareEntry.provenance(): String =
    listOfNotNull(
        report.title.takeIf { threadTitle != null },
        floor,
        report.target,
        report.generatedAt,
    ).joinToString(" · ")

/** Drawn where a report has no such row; screen readers are told [Res.string.report_compare_missing]. */
private const val MISSING = "—"

/** Shared by the cards and the sticky header so the header's columns sit over the cards' columns. */
private val CARD_PADDING = 12.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "这是测评对比空对比栏的预览")
@Composable
private fun ReportCompareEmptyPreview() {
    PlazaTheme {
        ReportCompareScreen(
            state = ReportCompareUiState(loaded = true),
            onBack = {},
            onToggle = {},
            onRemove = {},
            onOpenPost = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "这是测评对比三列对齐的预览")
@Composable
private fun ReportCompareTablePreview() {
    val entries =
        (1..3).map { n ->
            ReportCompareEntry(
                report =
                QualityReport(
                    title = "Report $n",
                    target = null,
                    generatedAt = null,
                    scriptVersion = null,
                    sections =
                    listOf(
                        QualityReport.Section(
                            title = "Section",
                            blocks =
                            listOfNotNull(
                                QualityReport.Block.Field("Label A", listOf(QualityReport.Value("Value $n"))),
                                QualityReport.Block.Field("Label B", listOf(QualityReport.Value("Value B"))).takeIf { n != 2 },
                            ),
                        ),
                    ),
                    footnotes = emptyList(),
                ),
            )
        }
    PlazaTheme {
        ReportCompareScreen(
            state =
            ReportCompareUiState(
                loaded = true,
                entries = entries,
                selected = entries,
                comparison = ReportComparison.of(entries.map { it.report }),
            ),
            onBack = {},
            onToggle = {},
            onRemove = {},
            onOpenPost = { _, _ -> },
        )
    }
}
