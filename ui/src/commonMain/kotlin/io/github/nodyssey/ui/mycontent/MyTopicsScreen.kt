package io.github.nodyssey.ui.mycontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.my_content_empty_action
import io.github.nodyssey.ui.resources.my_topics_count
import io.github.nodyssey.ui.resources.my_topics_empty_body
import io.github.nodyssey.ui.resources.my_topics_title
import io.github.nodyssey.ui.resources.space_empty_topics
import io.github.nodyssey.ui.space.SpacePostRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.StatusShapes
import org.jetbrains.compose.resources.stringResource

/**
 * 我的主题帖 — board n2.
 *
 * The row is the space page's own, because it is the same row: a title, the board when the payload
 * names one, and whatever counts came with it. Editing is not on it — that lives inside the thread
 * (board c3), and a per-row menu here would be a second place to get it wrong.
 */
@Composable
fun MyTopicsRoute(
    viewModel: MyTopicsViewModel,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onBrowseFeed: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyContentScreen(
        title = stringResource(Res.string.my_topics_title),
        state = state,
        countRes = Res.string.my_topics_count,
        emptyIcon = PlazaIcons.Article,
        emptyShape = StatusShapes.Empty,
        emptyTitle = stringResource(Res.string.space_empty_topics),
        emptyBody = stringResource(Res.string.my_topics_empty_body),
        emptyAction = stringResource(Res.string.my_content_empty_action),
        onEmptyAction = onBrowseFeed,
        onBack = onBack,
        onBoardSelected = viewModel::selectBoard,
        onSortSelected = viewModel::selectSort,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onVerify = onVerify,
        modifier = modifier,
    ) { post: SpacePost ->
        SpacePostRow(post = post, onClick = { onPostClick(post.postId) })
    }
}
