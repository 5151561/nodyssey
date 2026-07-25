package io.github.nsreader

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.login.WebViewScreen
import io.github.nsreader.ui.postdetail.PostDetailRoute
import io.github.nsreader.ui.postdetail.PostDetailViewModel
import io.github.nsreader.ui.postlist.PostListRoute
import io.github.nsreader.ui.postlist.PostListViewModel

@Composable
fun MainNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(PostListKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<PostListKey> {
                val viewModel: PostListViewModel =
                    viewModel(factory = PostListViewModel.factory(container))
                PostListRoute(
                    viewModel = viewModel,
                    onPostClick = { backStack.add(PostDetailKey(it)) },
                    onOpenBrowser = { backStack.add(WebKey(it, "NodeSeek")) },
                )
            }

            entry<PostDetailKey> { key ->
                // Keyed so navigating to a different post builds a fresh ViewModel.
                val viewModel: PostDetailViewModel = viewModel(
                    key = "post-${key.postId}",
                    factory = PostDetailViewModel.factory(container, key.postId),
                )
                PostDetailRoute(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBrowser = { backStack.add(WebKey(it, "NodeSeek")) },
                    onImageClick = { backStack.add(WebKey(it, "图片")) },
                )
            }

            entry<WebKey> { key ->
                WebViewScreen(
                    url = key.url,
                    title = key.title,
                    onClose = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
