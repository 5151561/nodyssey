package io.github.nsreader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.login.WebViewScreen
import io.github.nsreader.ui.navigation.ComingSoonScreen
import io.github.nsreader.ui.navigation.NodeSeekNavigationBar
import io.github.nsreader.ui.navigation.ProfileScreen
import io.github.nsreader.ui.navigation.TopLevelDestination
import io.github.nsreader.ui.postdetail.PostDetailRoute
import io.github.nsreader.ui.postdetail.PostDetailViewModel
import io.github.nsreader.ui.postlist.PostListRoute
import io.github.nsreader.ui.postlist.PostListViewModel

@Composable
fun MainNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(PostListKey)
    val signInUrl = NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH

    // The bar belongs to the top-level destinations only. A thread, the image viewer and the web
    // view are full-screen by design — showing a tab bar under them would invite leaving mid-read.
    val topLevel = TopLevelDestination.forKey(backStack.lastOrNull())

    Scaffold(
        bottomBar = {
            if (topLevel != null) {
                NodeSeekNavigationBar(
                    current = topLevel,
                    onSelect = { destination ->
                        if (destination != topLevel) {
                            // Tabs are roots, not a history: switching replaces the stack so that
                            // back always leaves the app rather than walking a trail of tabs.
                            backStack.clear()
                            backStack.add(destination.key)
                        }
                    },
                )
            }
        },
        // Each screen owns its own top inset; only the bar's height is passed down.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            entryProvider =
            entryProvider {
                entry<PostListKey> {
                    val viewModel: PostListViewModel =
                        viewModel(factory = PostListViewModel.factory(container))
                    PostListRoute(
                        viewModel = viewModel,
                        onPostClick = { backStack.add(PostDetailKey(it)) },
                        onOpenBrowser = { backStack.add(WebKey(it, "NodeSeek")) },
                    )
                }

                entry<SearchKey> {
                    ComingSoonScreen(title = stringResource(R.string.tab_search))
                }

                entry<NotificationsKey> {
                    ComingSoonScreen(title = stringResource(R.string.tab_notifications))
                }

                entry<ProfileKey> {
                    ProfileScreen(
                        onSignIn = { backStack.add(WebKey(signInUrl, "NodeSeek")) },
                    )
                }

                entry<PostDetailKey> { key ->
                    // Keyed so navigating to a different post builds a fresh ViewModel.
                    val viewModel: PostDetailViewModel =
                        viewModel(
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
}
