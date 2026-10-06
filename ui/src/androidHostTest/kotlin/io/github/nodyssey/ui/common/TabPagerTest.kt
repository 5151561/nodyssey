package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pager and the selected tab held in a ViewModel must name the same page once everything has
 * stopped moving, however a tap and a finger were interleaved to get there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class TabPagerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var selected by mutableIntStateOf(0)
    private lateinit var pagerState: PagerState

    private fun setPager() {
        composeRule.setContent {
            pagerState = rememberTabPagerState(selected, pageCount = 3, onPageSettled = { selected = it })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().testTag("pager")) { page ->
                Box(Modifier.fillMaxSize()) { BasicText("page $page") }
            }
        }
        composeRule.waitForIdle()
    }

    /** A few frames into the animation a selection starts, well short of halfway. */
    private fun selectAndRunAFewFrames(page: Int) {
        composeRule.mainClock.autoAdvance = false
        selected = page
        Snapshot.sendApplyNotifications()
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        check(pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction > 0f) {
            "expected the animation under way and short of halfway"
        }
    }

    @Test
    fun `a finger that takes a tapped tab's animation back to where it started deselects the tab`() {
        setPager()
        selectAndRunAFewFrames(1)

        composeRule.onNodeWithTag("pager").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.6f, 0f), delayMillis = 100)
            up()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(0, pagerState.currentPage)
        assertEquals(0, selected)
    }

    @Test
    fun `a second tap that cancels the first one's animation still lands on a page`() {
        setPager()
        selectAndRunAFewFrames(1)

        selected = 0
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(0f, pagerState.currentPageOffsetFraction)
        assertEquals(0, pagerState.currentPage)
        assertEquals(0, selected)
    }
}
