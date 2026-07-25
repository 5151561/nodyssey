package io.github.nsreader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.ui.common.NoSearchResultsState
import io.github.nsreader.ui.postlist.PostRow
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onPostClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onSearch = viewModel::submitSearch,
        onRecentClick = viewModel::selectRecentSearch,
        onRemoveRecent = viewModel::removeRecentSearch,
        onClearRecent = viewModel::clearRecentSearches,
        onPostClick = onPostClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRecentClick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onPostClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear_query),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            if (state.query.isBlank()) {
                if (state.recentSearches.isNotEmpty()) {
                    androidx.compose.foundation.layout.Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.search_recent),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearRecent) {
                            Text(stringResource(R.string.search_clear_all))
                        }
                    }
                    FlowRow(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        state.recentSearches.forEach { recent ->
                            InputChip(
                                selected = false,
                                onClick = { onRecentClick(recent) },
                                label = { Text(recent) },
                                trailingIcon = {
                                    IconButton(onClick = { onRemoveRecent(recent) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription =
                                            stringResource(R.string.search_remove_recent, recent),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            } else if (state.results.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    NoSearchResultsState(onClearQuery = { onQueryChange("") })
                }
            } else {
                Text(
                    text = stringResource(R.string.search_local_results, state.results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                LazyColumn {
                    items(state.results, key = { it.summary.postId }) { post ->
                        PostRow(post = post, onClick = { onPostClick(post.summary.postId) })
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SearchPreview() {
    NodeSeekTheme {
        SearchScreen(
            state = SearchUiState(recentSearches = listOf("腾讯云轻量", "nodequality", "DMIT")),
            onQueryChange = {},
            onSearch = {},
            onRecentClick = {},
            onRemoveRecent = {},
            onClearRecent = {},
            onPostClick = {},
        )
    }
}
