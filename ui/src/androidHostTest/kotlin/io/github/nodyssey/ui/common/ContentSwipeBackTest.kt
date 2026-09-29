package io.github.nodyssey.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class ContentSwipeBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backs = 0

    private fun setScreen(content: @Composable () -> Unit) {
        val dispatcher = NavigationEventDispatcher()
        dispatcher.addHandler(
            object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, true) {
                override fun onBackCompleted() {
                    backs++
                }
            },
        )
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                Box(Modifier.fillMaxSize().contentSwipeBack(enabled = true).testTag(SCREEN)) {
                    content()
                }
            }
        }
    }

    /** A right swipe across the middle of the screen at [y], well past the 30% a slow release needs. */
    private fun swipeRightAt(y: Float) =
        composeRule.onNodeWithTag(SCREEN).performTouchInput {
            swipe(Offset(width * 0.3f, y), Offset(width * 0.9f, y), durationMillis = 300)
        }

    @Test
    fun `a horizontal swipe over plain content goes back`() {
        setScreen {}

        swipeRightAt(400f)

        assertEquals(1, backs)
    }

    @Test
    fun `a scroller with room to move keeps the swipe`() {
        val scroll = ScrollState(initial = 500)
        setScreen { ScrollerRow(scroll) }

        swipeRightAt(50f)

        assertEquals(0, backs)
        assertTrue("the row should have scrolled back", scroll.value < 500)
    }

    @Test
    fun `a scroller already at its start lets the swipe through`() {
        val scroll = ScrollState(initial = 0)
        setScreen { ScrollerRow(scroll) }

        swipeRightAt(50f)

        assertEquals(1, backs)
    }

    @Test
    fun `a swipe that is mostly vertical scrolls the list instead`() {
        setScreen {
            val state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                items(100) { Box(Modifier.fillMaxWidth().height(40.dp)) }
            }
        }

        composeRule.onNodeWithTag(SCREEN).performTouchInput {
            swipe(Offset(width * 0.3f, 600f), Offset(width * 0.5f, 100f), durationMillis = 300)
        }

        assertEquals(0, backs)
    }

    @Composable
    private fun ScrollerRow(scroll: ScrollState) {
        Row(Modifier.fillMaxWidth().height(100.dp).horizontalScroll(scroll)) {
            repeat(10) { Box(Modifier.size(200.dp, 100.dp)) }
        }
    }

    private companion object {
        const val SCREEN = "screen"
    }
}
