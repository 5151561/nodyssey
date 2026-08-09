package io.github.bbs1

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.bbs1.di.AppContainer
import io.github.bbs1.ui.home.HomeScreen
import io.github.bbs1.ui.instances.InstancesScreen
import io.github.bbs1.ui.instances.InstancesViewModel

@Composable
fun Bbs1AppUi(container: AppContainer) {
    val viewModel: InstancesViewModel =
        viewModel { InstancesViewModel(container.instanceRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
    // or grow the stack when the switcher is the root.
    fun showCurrent() {
        if (HomeKey in backStack) backStack.removeLastOrNull() else backStack.add(HomeKey)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
        entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    current = state.current,
                    onOpenInstances = { backStack.add(InstancesKey) },
                )
            }
            entry<InstancesKey> {
                InstancesScreen(
                    state = state,
                    canNavigateBack = backStack.size > 1,
                    onBack = { backStack.removeLastOrNull() },
                    onSelect = { id ->
                        viewModel.select(id)
                        showCurrent()
                    },
                    onAdd = { baseUrl, name ->
                        viewModel.add(baseUrl, name)
                        showCurrent()
                    },
                    onRemove = viewModel::remove,
                )
            }
        },
    )
}
