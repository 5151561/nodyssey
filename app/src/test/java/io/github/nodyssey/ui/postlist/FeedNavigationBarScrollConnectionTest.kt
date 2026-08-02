package io.github.nodyssey.ui.postlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedNavigationBarScrollConnectionTest {
    @Test
    fun `advancing the feed hides the bar only after the direction threshold`() {
        val changes = mutableListOf<Boolean>()
        val connection = FeedNavigationBarScrollConnection(10f, changes::add)

        connection.onPreScroll(Offset(0f, -4f), NestedScrollSource.UserInput)
        connection.onPreScroll(Offset(0f, -5f), NestedScrollSource.UserInput)
        assertEquals(emptyList<Boolean>(), changes)

        connection.onPreScroll(Offset(0f, -1f), NestedScrollSource.UserInput)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `stopping does not reveal a hidden bar and reverse scrolling does`() {
        val changes = mutableListOf<Boolean>()
        val connection = FeedNavigationBarScrollConnection(10f, changes::add)
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
        val connection = FeedNavigationBarScrollConnection(10f, changes::add)
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)

        connection.reveal()
        connection.reveal()
        assertEquals(listOf(true, false), changes)

        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
        assertEquals(listOf(true, false, true), changes)
    }

    @Test
    fun `fling movement cannot reveal a hidden bar`() {
        val changes = mutableListOf<Boolean>()
        val connection = FeedNavigationBarScrollConnection(10f, changes::add)
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)

        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.SideEffect)

        assertEquals(listOf(true), changes)
    }
}
