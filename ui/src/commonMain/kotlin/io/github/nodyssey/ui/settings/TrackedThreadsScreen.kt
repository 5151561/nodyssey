package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.TrackedThread
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.tracked_threads_empty
import io.github.nodyssey.ui.resources.tracked_threads_title
import io.github.nodyssey.ui.resources.tracked_threads_untrack
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 追踪的帖子: the store's list as it stands, and a way to let one go. Nothing else to hold. */
class TrackedThreadsViewModel(
    private val store: TrackedThreadStore,
) : ViewModel() {
    val threads: StateFlow<List<TrackedThread>> =
        store.tracked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun untrack(postId: Long) {
        viewModelScope.launch { store.untrack(postId) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { TrackedThreadsViewModel(container.trackedThreadStore) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedThreadsRoute(
    viewModel: TrackedThreadsViewModel,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.tracked_threads_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LayerPageGutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (threads.isEmpty()) {
                Text(
                    stringResource(Res.string.tracked_threads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            } else {
                Column {
                    threads.forEachIndexed { index, thread ->
                        GroupedListItem(
                            first = index == 0,
                            last = index == threads.lastIndex,
                            onClick = { onOpenThread(thread.postId) },
                            headlineContent = { Text(thread.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.untrack(thread.postId) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(Res.string.tracked_threads_untrack, thread.title),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
