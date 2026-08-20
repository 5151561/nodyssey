package io.github.nodyssey

import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.ui.login.WebViewGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a two-pane window is showing, read off the back stack.
 *
 * The cases that matter are the ones where the answer is not the tab: a thread reached from a screen
 * that is not a list is still full-screen, and a list reached from a thread still counts.
 *
 * These also stand in for the scene metadata, which no test can read back: the entries carry none of
 * their own and `paneMetadataOf` derives all of it from [paneRoleOf].
 */
class PaneRoleTest {
    @Test
    fun `a thread under the feed shares the window with it`() {
        assertTrue(listOf<NavKey>(PostListKey, PostDetailKey(1)).showsListPane())
    }

    @Test
    fun `a thread under search shares the window with it`() {
        assertTrue(listOf<NavKey>(SearchKey, PostDetailKey(1)).showsListPane())
    }

    @Test
    fun `a conversation under the notification list shares the window with it`() {
        assertTrue(listOf<NavKey>(NotificationsKey, MessageThreadKey(7, "Alice")).showsListPane())
    }

    @Test
    fun `a thread opened from a user's space shares the window with it`() {
        val stack =
            listOf<NavKey>(ProfileKey, UserSpaceKey(uid = 7), PostDetailKey(1))
        assertTrue(stack.showsListPane())
    }

    @Test
    fun `a user's space opened from a thread is itself the list`() {
        val stack =
            listOf<NavKey>(PostListKey, PostDetailKey(1), UserSpaceKey(uid = 7))
        assertTrue(stack.showsListPane())
    }

    /**
     * 浏览历史 carries no pane metadata, so the scene strategy stops there and hands the whole window
     * to the thread. Answering otherwise would take away the only way back out of it.
     */
    @Test
    fun `a thread opened from reading history is full-screen`() {
        val stack = listOf<NavKey>(ProfileKey, ReadHistoryKey, PostDetailKey(1))
        assertFalse(stack.showsListPane())
    }

    @Test
    fun `a full-screen destination on top of a pair ends the run`() {
        val stack =
            listOf<NavKey>(
                PostListKey,
                PostDetailKey(1),
                WebKey("https://example.test", "NodeSeek", WebViewGoal.MANAGE),
            )
        assertFalse(stack.showsListPane())
    }

    @Test
    fun `我的 is a menu, not a list`() {
        assertEquals(null, paneRoleOf(ProfileKey))
        assertFalse(listOf<NavKey>(ProfileKey).showsListPane())
    }

    @Test
    fun `every list pane says something different when its detail is empty`() {
        val listKeys = listOf<NavKey>(PostListKey, SearchKey, NotificationsKey, UserSpaceKey(uid = 7))
        // Distinct implies present: a key with no line of its own throws rather than returning 0.
        assertEquals(listKeys.size, listKeys.map { emptyDetailTextOf(it) }.toSet().size)
    }

    @Test
    fun `the three list roots and the two details keep their roles`() {
        listOf<NavKey>(PostListKey, SearchKey, NotificationsKey, UserSpaceKey(uid = 7)).forEach {
            assertEquals(PaneRole.LIST, paneRoleOf(it))
        }
        listOf<NavKey>(PostDetailKey(1), MessageThreadKey(7, "Alice")).forEach {
            assertEquals(PaneRole.DETAIL, paneRoleOf(it))
        }
    }
}
