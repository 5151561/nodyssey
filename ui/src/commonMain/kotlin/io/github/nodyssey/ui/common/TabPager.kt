package io.github.nodyssey.ui.common

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * 左右滑动切换 tab: a [PagerState] kept in step with a selected tab that lives in a ViewModel.
 *
 * Both directions. A page the finger brings to rest becomes the selection through [onPageSettled] —
 * settled rather than current, because anything before rest is a gesture still being made, and
 * selecting mid-swipe would start a tab's load under a finger that had not chosen yet. A selection
 * made elsewhere — a tab tapped, a tab restored with the screen — animates the pager to it.
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
        snapshotFlow { pagerState.settledPage }.collect { page ->
            // Coming to rest on the selected tab says nothing — that is every first composition, and
            // every page the effect below was the one to move.
            if (page != currentSelected && page < pagerState.pageCount) currentOnPageSettled(page)
        }
    }
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    return pagerState
}
