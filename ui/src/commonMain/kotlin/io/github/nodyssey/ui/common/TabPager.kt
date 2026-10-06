package io.github.nodyssey.ui.common

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 左右滑动切换 tab: a [PagerState] kept in step with a selected tab that lives in a ViewModel.
 *
 * Both directions. A page the pager comes to rest on becomes the selection — at rest rather than
 * current, because anything before rest is a gesture still being made, and selecting mid-swipe would
 * start a tab's load under a finger that had not chosen yet. A selection made elsewhere — a tab
 * tapped, a tab restored with the screen — moves the pager to it; see [settleOn] for how.
 *
 * Draw the tab row from [PagerState.currentPage] rather than from the selection, so the indicator
 * crosses over at the halfway point of the swipe instead of jumping once the page lands.
 */
@Composable
internal fun rememberTabPagerState(
    selectedIndex: Int,
    pageCount: Int,
    onPageSettled: (Int) -> Unit,
): PagerState {
    val pagerState = rememberPagerState(initialPage = selectedIndex) { pageCount }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)
    LaunchedEffect(pagerState) {
        pagerState.collectPagesAtRest { page ->
            // Coming to rest on the selected tab says nothing — that is every first composition, and
            // every page the effect below was the one to move.
            if (page != currentSelected && page < pagerState.pageCount) currentOnPageSettled(page)
        }
    }
    LaunchedEffect(selectedIndex) { pagerState.settleOn(selectedIndex) }
    return pagerState
}

/**
 * Calls [onRest] with the page every time the pager stops moving, whoever moved it.
 *
 * Not [PagerState.settledPage]: that is frozen at the page a scroll *started* from, and a drag that
 * cuts into a scroll already running — a tapped tab's animation — starts nothing new, so a finger
 * that took the animation back to where it began left `settledPage` unchanged throughout. The
 * selection had already moved on and was never told; the pager showed one tab and the ViewModel
 * believed in another. Every rest is reported here, and the caller drops the ones that agree.
 *
 * A rest has to outlast one frame. A scroll that cancels another — a second tab tapped mid-animation,
 * a finger landing on one — leaves a gap between the first one's end and the second one's start,
 * and that gap is not a page anybody chose.
 */
internal suspend fun PagerState.collectPagesAtRest(onRest: (Int) -> Unit) {
    snapshotFlow { if (isScrollInProgress) null else currentPage }.collectLatest { page ->
        if (page == null) return@collectLatest
        withFrameNanos {}
        onRest(page)
    }
}

/**
 * Moves the pager onto [page] for a selection made somewhere other than the pager.
 *
 * Off a page boundary counts as not there yet even when [PagerState.currentPage] already names
 * [page]: a second tap that cancels the first one's animation before it crosses halfway names the
 * page the pager is still nearest to, and returning on the index alone left it parked between two
 * pages. Only a step to the immediate neighbour is animated — a distant tab would otherwise fly
 * through every page between, composing each one and starting its load on the way past.
 */
internal suspend fun PagerState.settleOn(page: Int) {
    if (currentPage == page && currentPageOffsetFraction == 0f) return
    if (abs(currentPage - page) <= 1) animateScrollToPage(page) else scrollToPage(page)
}

/**
 * The [HorizontalPager] under a tab row, for a screen that is pushed — one a swipe can go back from.
 *
 * On iOS a swipe towards the previous tab from the first one is the system's back gesture, the way a
 * `UIScrollView` at its leading edge lets `UINavigationController` have it. A pager cannot do that by
 * itself: it takes the drag once it passes touch slop whether or not it has a page to go to, and
 * Compose Multiplatform stops UIKit's swipe-anywhere pop the moment any drag in the page is taken —
 * so a screen with tabs could only be left by its arrow. At rest on the first page, then, the pager's
 * own drag is switched off and [firstPageLeavesBackSwipe] stands in for it in one direction only;
 * everywhere else it is the pager's. Everywhere but iOS this is a plain [HorizontalPager].
 */
@Composable
internal fun TabPager(
    state: PagerState,
    key: (index: Int) -> Any,
    modifier: Modifier = Modifier,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        key = key,
        modifier = modifier.firstPageLeavesBackSwipe(state),
        userScrollEnabled = !contentSwipeBackSupported || state.canScrollBackward,
        pageContent = pageContent,
    )
}

/**
 * The pager's drag at rest on its first page, towards the next page only: a drag the other way is
 * never taken, so it stays UIKit's to turn into a pop.
 *
 * Hand-rolled because the pager's own drag (`Modifier.scrollable` underneath) has no say over which
 * direction it claims. It drives the pager the way that drag does — deltas inside one user-input
 * [PagerState.scroll], then [PagerDefaults.flingBehavior] to settle — so a swipe that starts here and
 * one that starts on any other page land alike. Remove it if Compose Multiplatform lets a scroller at
 * its edge pass the drag to UIKit's pop the way a `UIScrollView` does.
 */
@Composable
private fun Modifier.firstPageLeavesBackSwipe(state: PagerState): Modifier {
    if (!contentSwipeBackSupported) return this
    val fling = PagerDefaults.flingBehavior(state)
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The pager scrolls forward when the finger moves towards the leading edge.
    val toScroll = { dx: Float -> if (rtl) dx else -dx }
    return pointerInput(state, fling, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Off the first page, or still moving, the pager's own drag is on.
            if (state.canScrollBackward) return@awaitEachGesture
            var overSlop = 0f
            val start =
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    // Left unconsumed, a drag towards back is never claimed here.
                    if (toScroll(over) > 0f) {
                        overSlop = over
                        change.consume()
                    }
                } ?: return@awaitEachGesture
            val velocity = VelocityTracker().apply { addPointerInputChange(start) }
            val deltas = Channel<Float>(Channel.UNLIMITED)
            val release = CompletableDeferred<Float>()
            scope.launch {
                state.scroll(MutatePriority.UserInput) {
                    for (delta in deltas) scrollBy(delta)
                    with(fling) { performFling(release.await()) }
                }
            }
            deltas.trySend(toScroll(overSlop))
            val lifted =
                horizontalDrag(start.id) { change ->
                    velocity.addPointerInputChange(change)
                    deltas.trySend(toScroll(change.positionChange().x))
                    change.consume()
                }
            deltas.close()
            // A cancelled drag still settles, onto whichever page is nearer.
            release.complete(if (lifted) toScroll(velocity.calculateVelocity().x) else 0f)
        }
    }
}
