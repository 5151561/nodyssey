package io.github.nodyssey

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.composer.PostComposerRoute
import io.github.nodyssey.ui.composer.PostComposerViewModel
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.postdetail.PostDetailRoute
import io.github.nodyssey.ui.postdetail.PostDetailViewModel
import io.github.nodyssey.ui.stardust.StardustReceiveCard
import io.github.nodyssey.ui.stardust.StardustReceiveViewModel
import io.github.nodyssey.ui.viewer.ImageViewerScreen
import io.github.nodyssey.ui.viewer.ImageViewerViewModel
import io.github.nodyssey.ui.viewer.rememberImageGallerySaver
import io.github.nodyssey.ui.vote.VoteCard
import io.github.nodyssey.ui.vote.VoteViewModel

/**
 * The reading-and-writing core: a thread, the composer, and the image viewer.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.threadEntries(nav: StackEntryScope) = with(nav) {
    entry<ImageViewerKey>(
        /*
         * Fades rather than the default slide.
         *
         * Every other destination is a page that follows the one before it, and sliding says
         * so. This one is the picture already on screen, filled out — it is the same object
         * seen closer, not a place the user travelled to, and sliding it in from the edge
         * reads as having left the thread rather than having zoomed into it.
         */
        metadata =
        NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
            NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
            NavDisplay.predictivePopTransitionSpec { _ ->
                fadeIn() togetherWith fadeOut()
            },
    ) { key ->
        val saver = rememberImageGallerySaver(container.dispatchers)
        val viewModel: ImageViewerViewModel =
            viewModel(factory = ImageViewerViewModel.factory(saver))
        val saveOutcome by viewModel.saveOutcome.collectAsStateWithLifecycle()
        ImageViewerScreen(
            urls = key.urls,
            initialIndex = key.index,
            onClose = { backStack.removeLastOrNull() },
            // The browser, even for an image hosted on nodeseek.com: this one is the user
            // reaching for downloading and sharing, which is the browser's job and not
            // something the session's web view does better.
            onOpenBrowser = openExternalUrl,
            saveOutcome = saveOutcome,
            onSave = viewModel::save,
        )
    }

    entry<PostDetailKey>(
        /*
         * Fades rather than the default slide — but only for a thread opened from a row that
         * handed over its title.
         *
         * A slide and a shared element contradict each other: the title would detach from
         * the page it belongs to and fly across a screen that is itself travelling sideways.
         * Fading the two pages leaves the title as the only thing moving, which is the whole
         * point of moving it. Where no row supplied a title there is nothing to fly — a
         * notification, a deep link, the composer — and those keep the slide, which is still
         * the honest description of what happened: a page arrived from somewhere else.
         */
        metadata = { key ->
            if (key.preview == null) emptyMap() else ThreadOpenTransition
        },
    ) { key ->
        // Keyed so navigating to a different post builds a fresh ViewModel.
        val viewModel: PostDetailViewModel =
            viewModel(
                key = "post-${key.postId}",
                factory =
                PostDetailViewModel.factory(
                    container,
                    key.postId,
                    initialFloor = key.floor,
                    initialPage = key.page,
                    preview = key.preview,
                ),
            )
        // Its own ViewModel, keyed the same way: an unsent reply belongs to one thread
        // and has to outlive the sheet that shows it.
        val replyViewModel: ReplyComposerViewModel =
            viewModel(
                key = "reply-${key.postId}",
                factory = ReplyComposerViewModel.factory(container, key.postId),
            )
        PostDetailRoute(
            viewModel = viewModel,
            replyViewModel = replyViewModel,
            showBackButton = !(isListDetailExpanded() && backStack.showsListPane()),
            onBack = { backStack.removeLastOrNull() },
            onOpenBrowser = openWebUrl,
            onLinkClick = openContentUrl,
            onAuthorClick = openSpace,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
            onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
            onEdit = { target -> backStack.add(PostComposerKey(target)) },
            // Supplied here because this is the only layer that can reach the container.
            // Keyed by vote id and not merely by post: a thread may embed more than one, and
            // without the key they would share a single ViewModel and each other's state.
            voteContent = { voteId ->
                VoteCard(
                    viewModel =
                    viewModel(
                        key = "vote-$voteId",
                        factory = VoteViewModel.factory(container, voteId),
                    ),
                    onSignIn = { backStack.add(SignInKey) },
                    onUserClick = openSpace,
                )
            },
            // Keyed by payee *and* Ref ID, for the same reason a vote is keyed by its id: one
            // post may carry several codes, and a shared ViewModel would show one code's
            // tally under another's amount.
            stardustContent = { node ->
                StardustReceiveCard(
                    node = node,
                    viewModel =
                    viewModel(
                        key = "stardust-${node.memberId}-${node.refId}",
                        factory = StardustReceiveViewModel.factory(container, node),
                    ),
                    onSignIn = { backStack.add(SignInKey) },
                )
            },
        )
    }

    entry<PostComposerKey> { key ->
        val viewModel: PostComposerViewModel =
            viewModel(
                // Keyed by what is being written, or two edits opened in one session would
                // share the first one's ViewModel — and therefore the first one's text.
                key = "composer-${key.edit?.commentId ?: "new"}",
                factory = PostComposerViewModel.factory(container, key.edit),
            )
        // Whatever this editor is about: the thread when editing, the new-post page otherwise.
        val webUrl =
            key.edit
                ?.let { NodeSeekSite.BASE_URL + NodeSeekSite.postPath(it.postId, it.page) }
                ?: (NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH)
        PostComposerRoute(
            viewModel = viewModel,
            onClose = { backStack.removeLastOrNull() },
            onSignIn = {
                backStack.add(SignInKey)
            },
            onVerify = {
                backStack.add(WebKey(webUrl, siteTitle, WebViewGoal.CHALLENGE))
            },
            onOpenBrowser = {
                backStack.add(WebKey(webUrl, siteTitle, WebViewGoal.MANAGE))
            },
            onPublished = { postId ->
                backStack.removeLastOrNull()
                // An edit returns to the thread it came from, which is already underneath —
                // pushing it again would stack a second copy of the screen being updated.
                if (key.edit == null) postId?.let { backStack.add(PostDetailKey(it)) }
            },
        )
    }
}

/**
 * What a thread opened from a list row animates as. See the `entry<PostDetailKey>` metadata for why
 * this is not the default slide, and [io.github.nodyssey.ui.common.sharedThreadTitle] for the thing
 * the fade is clearing the way for.
 */
private val ThreadOpenTransition =
    NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.predictivePopTransitionSpec { _ -> fadeIn() togetherWith fadeOut() }

private fun imageViewerKeyFor(urls: List<String>, url: String): ImageViewerKey =
    ImageViewerKey(urls = urls, index = urls.indexOf(url).coerceAtLeast(0))
