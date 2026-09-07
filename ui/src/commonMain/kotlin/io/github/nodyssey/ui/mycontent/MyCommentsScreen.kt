package io.github.nodyssey.ui.mycontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.my_comments_count
import io.github.nodyssey.ui.resources.my_comments_empty_body
import io.github.nodyssey.ui.resources.my_comments_title
import io.github.nodyssey.ui.resources.my_content_empty_action
import io.github.nodyssey.ui.resources.space_empty_comments
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.StatusShapes
import org.jetbrains.compose.resources.stringResource

/**
 * 我的评论 — board n3.
 *
 * Tapping a row opens the thread at that floor, which is the whole point of keeping the floor on
 * [SpaceComment]: the archive is only useful if it can put you back where you said it.
 */
@Composable
fun MyCommentsRoute(
    viewModel: MyCommentsViewModel,
    onBack: () -> Unit,
    onCommentClick: (Long, String?) -> Unit,
    onBrowseFeed: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyContentScreen(
        title = stringResource(Res.string.my_comments_title),
        state = state,
        countRes = Res.string.my_comments_count,
        emptyIcon = PlazaIcons.ChatBubble,
        emptyShape = StatusShapes.Empty,
        emptyTitle = stringResource(Res.string.space_empty_comments),
        emptyBody = stringResource(Res.string.my_comments_empty_body),
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
    ) { comment: SpaceComment ->
        MyCommentRow(
            postTitle = comment.postTitle,
            excerpt = comment.excerpt,
            floor = comment.floor,
            createdAtText = comment.createdAtText,
            onClick = { onCommentClick(comment.postId, comment.floor) },
        )
    }
}
