package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.TrackedThread
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.nodyssey.ui.resources.tracked_threads_empty
import io.github.nodyssey.ui.resources.tracked_threads_title
import io.github.nodyssey.ui.resources.tracked_threads_untrack
import io.github.plaza.designsys.component.GroupDividerInset
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.ThreadRowTitle
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
                        TrackedThreadRow(
                            thread = thread,
                            first = index == 0,
                            last = index == threads.lastIndex,
                            onClick = { onOpenThread(thread.postId) },
                            onUntrack = { viewModel.untrack(thread.postId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One followed thread, drawn the way 收藏 draws a collected one: the title, then the board, the
 * author and how many replies the reader has been told about, with 取消追踪 where 收藏 keeps its
 * download state.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackedThreadRow(
    thread: TrackedThread,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onUntrack: () -> Unit,
) {
    GroupedListItem(
        first = first,
        last = last,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        dividerInset = GroupDividerInset,
        verticalAlignment = ListItemDefaults.verticalAlignment(),
        headlineContent = { ThreadRowTitle(text = AnnotatedString(thread.title)) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                BoardTag(title = thread.categoryTitle, slug = thread.categorySlug)
                // A count below zero is "not seen yet" — nothing to print until a poll has looked.
                val replies = thread.lastKnownCount.takeIf { it >= 0 }?.let { stringResource(Res.string.post_reply_count, it) }
                val byline = listOfNotNull(thread.authorName?.takeIf { it.isNotBlank() }, replies)
                if (byline.isNotEmpty()) MetaText(byline.joinToString(" · "), singleLine = true)
            }
        },
        trailingContent = {
            IconButton(onClick = onUntrack) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.tracked_threads_untrack, thread.title))
            }
        },
    )
}
