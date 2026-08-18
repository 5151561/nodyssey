package io.github.nodyssey.ui.common

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * The scope a thread's own things travel in on their way from a list row to the thread it opens, or
 * null wherever they must not travel.
 *
 * Null in two situations, and both matter:
 *
 * - Outside `NavDisplay` entirely — a `@Preview`, a screen test. Every modifier below reads this
 *   before it reaches for `LocalNavAnimatedContentScope`, which throws rather than defaulting when
 *   nothing has provided it, so a screen rendered on its own draws plain content and no scope is
 *   touched.
 * - On a window wide enough for two panes. There the list is not replaced by the thread, it sits
 *   beside it, so a row and the header for the same post are on screen *at the same time* — two
 *   live claims on one shared-element key, which is a state the shared-transition machinery has no
 *   answer for. Nothing travels in that layout because nothing goes anywhere: the row stays exactly
 *   where it was.
 */
val LocalThreadTransition = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The four things a feed row and a thread state about the same post.
 *
 * A row is not a summary of the thread so much as the same four facts laid out in one line, and
 * opening the thread re-lays them out rather than replacing them: the title grows and takes the
 * width, the board tag drops under it, the avatar grows and takes the author's name down with it.
 * Animating that is only saying out loud what the two layouts already have in common — which is
 * why the *time* is not in this list. The row shows when the thread was last active and the thread
 * shows when it was posted; those are two different facts that happen to look alike.
 *
 * Every one of them is keyed by post id, which is what pairs the two ends. A row whose thread is
 * opened from somewhere else, or a thread opened from a notification, simply never finds its
 * partner — that costs nothing and animates as it always did.
 */
private enum class ThreadPart {
    TITLE,
    AVATAR,
    AUTHOR,
    BOARD,
}

/**
 * The title, which the row states in 15sp over two lines at most and the thread in 20sp over as many
 * as it takes.
 *
 * [SharedTransitionScope.sharedBounds] and not `sharedElement`, because the two are the same *title*
 * and not the same piece of text — only the box they occupy can be interpolated honestly.
 * `scaleToBounds` scales the outgoing text into that travelling box instead of re-wrapping it every
 * frame, which is what stops a title that wraps differently at the two ends from reflowing in
 * flight.
 */
@Composable
fun Modifier.sharedThreadTitle(postId: Long): Modifier =
    sharedThreadBounds(ThreadPart.TITLE, postId)

/**
 * The author's name: 12sp in the row's meta line, `titleSmall` beside the larger avatar in the
 * thread. Bounds again, for the title's reason — same name, different type.
 */
@Composable
fun Modifier.sharedThreadAuthor(postId: Long): Modifier =
    sharedThreadBounds(ThreadPart.AUTHOR, postId)

/**
 * The avatar: 34dp in the row, 40dp in the thread, and the *same picture* at both ends — so this one
 * is a true [SharedTransitionScope.sharedElement], which scales its content rather than crossfading
 * two versions of it. [io.github.plaza.designsys.component.AvatarShape] states its corner as a
 * percentage, so the rounding scales along with it and stays right at every frame in between.
 *
 * Not applied to a pinned row, which draws a pin where the avatar goes. Flying a pin into a face
 * would be a claim that they are the same thing.
 */
@Composable
fun Modifier.sharedThreadAvatar(postId: Long): Modifier =
    sharedThreadElement(ThreadPart.AVATAR, postId)

/**
 * The board tag, which is the one part drawn identically at both ends — same component, same text,
 * same tonal colours. Nothing to crossfade, so it travels as a [SharedTransitionScope.sharedElement]
 * and simply moves.
 */
@Composable
fun Modifier.sharedThreadBoard(postId: Long): Modifier =
    sharedThreadElement(ThreadPart.BOARD, postId)

/**
 * The app's own spatial motion, so everything that travels does so on the same curve as the rest of
 * the app — and, more to the point, as each other. Four things leaving one row for four places have
 * to arrive together or they read as four animations rather than one layout rearranging.
 */
@Composable
private fun threadBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return BoundsTransform { _, _ -> spec }
}

@Composable
private fun Modifier.sharedThreadBounds(
    part: ThreadPart,
    postId: Long,
): Modifier {
    val transition = LocalThreadTransition.current ?: return this
    // Deliberately read only after the null check above: it throws outside a NavDisplay entry.
    val animatedScope = LocalNavAnimatedContentScope.current
    val boundsTransform = threadBoundsTransform()
    return with(transition) {
        this@sharedThreadBounds.sharedBounds(
            sharedContentState = rememberSharedContentState("${part.name}-$postId"),
            animatedVisibilityScope = animatedScope,
            enter = fadeIn(),
            exit = fadeOut(),
            boundsTransform = boundsTransform,
            resizeMode =
            SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopStart,
            ),
        )
    }
}

@Composable
private fun Modifier.sharedThreadElement(
    part: ThreadPart,
    postId: Long,
): Modifier {
    val transition = LocalThreadTransition.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    val boundsTransform = threadBoundsTransform()
    return with(transition) {
        this@sharedThreadElement.sharedElement(
            sharedContentState = rememberSharedContentState("${part.name}-$postId"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = boundsTransform,
        )
    }
}
