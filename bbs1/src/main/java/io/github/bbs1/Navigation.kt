package io.github.bbs1

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.bbs1.di.AppContainer
import io.github.bbs1.ui.home.HomeScreen
import io.github.bbs1.ui.home.HomeViewModel
import io.github.bbs1.ui.instances.InstancesScreen
import io.github.bbs1.ui.instances.InstancesViewModel
import io.github.bbs1.ui.topic.TopicScreen
import io.github.bbs1.ui.topic.TopicViewModel

@Composable
fun Bbs1AppUi(container: AppContainer) {
    val instancesViewModel: InstancesViewModel =
        viewModel { InstancesViewModel(container.instanceRepository, container.api) }
    val state by instancesViewModel.uiState.collectAsStateWithLifecycle()
    val addState by instancesViewModel.addState.collectAsStateWithLifecycle()

    if (state.loading) {
        // The first DataStore read is in flight. Starting the back stack now would bake "no sites"
        // into the start destination before knowing whether that is true.
        Box(Modifier.fillMaxSize())
        return
    }

    // Evaluated once, at the first composition after loading: a user with a site lands on it, a new
    // user lands on the site list. Later changes navigate; they do not re-root the stack.
    val startAtHome = remember { state.currentId != null }
    val backStack = rememberNavBackStack(if (startAtHome) HomeKey else InstancesKey)

    // Selecting or adding a site should land on it: pop back to the home already under the switcher,
    // or grow the stack when the switcher is the root. Relies on the stack holding at most one
    // HomeKey — true today because Home only ever enters through here, and this pushes only when no
    // Home is present. A deeper hierarchy that adds another way onto Home must keep that invariant,
    // or this pop lands on the wrong entry. (A TopicKey never sits between them either: the site
    // switcher is reachable only from Home, so switching away always happens with Home on top.)
    fun showCurrent() {
        if (HomeKey in backStack) backStack.removeLastOrNull() else backStack.add(HomeKey)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Stated because the ViewModel decorator is not among NavDisplay's defaults: without it,
        // every entry's viewModel {} would resolve to the activity and outlive its screen.
        entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        ),
        entryProvider =
        entryProvider {
            entry<HomeKey> {
                val homeViewModel: HomeViewModel =
                    viewModel { HomeViewModel(container.instanceRepository, container.api) }
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = homeState,
                    onOpenInstances = { backStack.add(InstancesKey) },
                    onOpenTopic = { id -> backStack.add(TopicKey(id)) },
                    onSelectForum = homeViewModel::selectForum,
                    onLoadMore = homeViewModel::loadMore,
                    onRetryAppend = homeViewModel::retryAppend,
                    onRefresh = homeViewModel::refresh,
                )
            }
            entry<TopicKey> { key ->
                val baseUrl = state.current?.baseUrl
                if (baseUrl == null) {
                    // The site vanished under a restored stack (removed on another device before a
                    // backup restore, say). There is nothing to load this thread from; leave.
                    LaunchedEffect(Unit) { backStack.removeLastOrNull() }
                    Box(Modifier.fillMaxSize())
                } else {
                    val topicViewModel: TopicViewModel =
                        viewModel { TopicViewModel(container.api, baseUrl, key.id) }
                    val topicState by topicViewModel.uiState.collectAsStateWithLifecycle()
                    TopicScreen(
                        state = topicState,
                        baseUrl = baseUrl,
                        onBack = { backStack.removeLastOrNull() },
                        onLoadMore = topicViewModel::loadMore,
                        onRetryAppend = topicViewModel::retryAppend,
                        onRefresh = topicViewModel::refresh,
                    )
                }
            }
            entry<InstancesKey> {
                InstancesScreen(
                    state = state,
                    addState = addState,
                    canNavigateBack = backStack.size > 1,
                    onBack = { backStack.removeLastOrNull() },
                    onSelect = { id ->
                        instancesViewModel.select(id)
                        showCurrent()
                    },
                    onAddSubmit = instancesViewModel::add,
                    onAdded = ::showCurrent,
                    onAddConsumed = instancesViewModel::consumeAdd,
                    onRemove = instancesViewModel::remove,
                )
            }
        },
    )
}
