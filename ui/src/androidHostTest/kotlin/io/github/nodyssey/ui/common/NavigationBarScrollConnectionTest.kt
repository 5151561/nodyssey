package io.github.nodyssey.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationBarScrollConnectionTest {
    @Test
    fun `advancing the feed hides the bar only after the direction threshold`() {
        val changes = mutableListOf<Boolean>()
        val connection = NavigationBarScrollConnection(10f, changes::add)

        connection.onPreScroll(Offset(0f, -4f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -5f), NestedScrollSource.UserInput)
        assertEquals(emptyList<Boolean>(), changes)

        connection.onPreScroll(Offset(0f, -1f), NestedScrollSource.UserInput)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `stopping does not reveal a hidden bar and reverse scrolling does`() {
        val changes = mutableListOf<Boolean>()
        val connection = NavigationBarScrollConnection(10f, changes::add)
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)

        connection.resetGesture()
        assertEquals(listOf(true), changes)

        connection.onPreScroll(Offset(0f, 6f), NestedScrollSource.UserInput)
        assertEquals(listOf(true), changes)

        connection.onPreScroll(Offset(0f, 4f), NestedScrollSource.UserInput)
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `revealing reports once and leaves the bar hideable again`() {
        val changes = mutableListOf<Boolean>()
        val connection = NavigationBarScrollConnection(10f, changes::add)
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)

        connection.reveal()
        connection.reveal()
        assertEquals(listOf(true, false), changes)

        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
        assertEquals(listOf(true, false, true), changes)
    }

    /**
     * The screens no longer watch for the gesture ending, so the connection has to drop the distance
     * a swipe did not spend by itself — otherwise the next swipe starts part-way to the threshold.
     */
    @Test
    fun `the end of a gesture drops what it did not spend`() = runTest {
        val changes = mutableListOf<Boolean>()
        val connection = NavigationBarScrollConnection(10f, changes::add)

        connection.onPreScroll(Offset(0f, -9f), NestedScrollSource.UserInput)
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
        connection.onPreScroll(Offset(0f, -9f), NestedScrollSource.UserInput)

        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test
    fun `fling movement cannot reveal a hidden bar`() {
        val changes = mutableListOf<Boolean>()
        val connection = NavigationBarScrollConnection(10f, changes::add)
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)

        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.SideEffect)

        assertEquals(listOf(true), changes)
    }
}
