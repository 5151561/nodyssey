package io.github.nodyssey.ui.mycontent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.my_content_all_boards
import io.github.nodyssey.ui.resources.my_content_end
import io.github.nodyssey.ui.resources.my_content_sort_newest
import io.github.nodyssey.ui.resources.my_content_sort_oldest
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The frame boards n2 and n3 share: app bar, the two filter chips, the count, then the list.
 *
 * Rows append as the list is scrolled — the same continuous load the feed uses, and the reason
 * [MyContentViewModel] keeps its pages in memory instead of streaming a `PagingData` the chips
 * could never reorder.
 */
@Composable
internal fun <T : Any> MyContentScreen(
    title: String,
    state: MyContentUiState<T>,
    /** "共 %1$d 篇" / "共 %1$d 条" — the site's total, formatted for this list's unit. */
    countRes: StringResource,
    emptyIcon: ImageVector,
    emptyShape: Shape,
    emptyTitle: String,
    emptyBody: String,
    emptyAction: String,
    onEmptyAction: () -> Unit,
    onBack: () -> Unit,
    onBoardSelected: (String?) -> Unit,
    onSortSelected: (MyContentSort) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = title,
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .readableWidth(),
        ) {
            MyContentFilters(
                state = state,
                countRes = countRes,
                onBoardSelected = onBoardSelected,
                onSortSelected = onSortSelected,
            )
            when {
                state.loadedCount == 0 && state.isLoading -> LoadingState()

                state.loadedCount == 0 && state.error != null ->
                    SiteErrorState(
                        error = state.error,
                        onRetry = onRetry,
                        onOpenBrowser = onOpenBrowser,
                        onSignIn = onSignIn,
                        onVerify = onVerify,
                    )

                state.isEmpty ->
                    StatusView(
                        icon = emptyIcon,
                        shape = emptyShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = emptyTitle,
                        description = emptyBody,
                        primaryAction = StatusAction(emptyAction, onEmptyAction),
                    )

                else -> MyContentList(state, onLoadMore, onRetry, row)
            }
        }
    }
}

@Composable
private fun <T : Any> MyContentFilters(
    state: MyContentUiState<T>,
    countRes: StringResource,
    onBoardSelected: (String?) -> Unit,
    onSortSelected: (MyContentSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only once a row has actually carried a board. The comment endpoint never does and the
        // topic one usually does not, and a chip whose menu is empty is a filter that cannot filter.
        if (state.boards.isNotEmpty()) {
            val all = stringResource(Res.string.my_content_all_boards)
            MenuChip(
                label = state.board ?: all,
                options = listOf(all) + state.boards,
                onSelected = { index ->
                    onBoardSelected(if (index == 0) null else state.boards[index - 1])
                },
            )
        }
        MenuChip(
            label = stringResource(state.sort.labelRes()),
            options = MyContentSort.entries.map { stringResource(it.labelRes()) },
            onSelected = { index -> onSortSelected(MyContentSort.entries[index]) },
        )
        Box(Modifier.weight(1f))
        // The site's own total, not what has been loaded: this line answers "how many do I have",
        // and it must not creep upward as pages arrive.
        state.totalCount?.let {
            Text(
                text = stringResource(countRes, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun MyContentSort.labelRes(): StringResource =
    when (this) {
        MyContentSort.NEWEST -> Res.string.my_content_sort_newest
        MyContentSort.OLDEST -> Res.string.my_content_sort_oldest
    }

/** A filter chip that opens its own menu — what boards n2 and n3 draw for both filters. */
@Composable
private fun MenuChip(
    label: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label) },
            shape = RoundedCornerShape(8.dp),
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun <T : Any> MyContentList(
    state: MyContentUiState<T>,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, state.items.size, onLoadMore)
    LazyColumn(state = listState) {
        items(state.items.size) { index ->
            row(state.items[index])
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item(key = "footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                MyContentFooter(state, onRetry)
            }
        }
    }
}

@Composable
private fun <T : Any> MyContentFooter(
    state: MyContentUiState<T>,
    onRetry: () -> Unit,
) {
    when {
        state.error != null ->
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }

        state.endReached ->
            Text(
                text = stringResource(Res.string.my_content_end),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        else -> PlazaSpinner(Modifier.describedAsLoading(), size = 22.dp)
    }
}

/**
 * Asks for the next page once the end of the loaded rows is in sight.
 *
 * Keyed on the row count as well as the list state so that an arriving page re-arms it; without
 * that, a short page which does not fill the viewport would stall the list for good.
 */
@Composable
private fun LoadMoreWhenNearEnd(
    listState: LazyListState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, itemCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= itemCount - LOAD_MORE_LOOKAHEAD) onLoadMore() }
    }
}

private const val LOAD_MORE_LOOKAHEAD = 3

/**
 * One comment, the way board n3 draws it: the thread it belongs to on a tonal bar, then what was
 * said, then the floor and the time.
 *
 * The quote bar carries no board tag, unlike the mock. `/api/content/list-comments` returns the
 * thread's title and nothing else about it, and colouring in a board for it would be a guess.
 */
@Composable
internal fun MyCommentRow(
    postTitle: String?,
    excerpt: String,
    floor: String?,
    createdAtText: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        postTitle?.let {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
        Text(text = excerpt, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            floor?.let { MetaText(it) }
            createdAtText?.let { MetaText(it) }
        }
    }
}

@Composable
private fun RowScope.MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
