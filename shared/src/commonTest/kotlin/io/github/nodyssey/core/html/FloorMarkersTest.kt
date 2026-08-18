package io.github.nodyssey.core.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Badge stacks, the edited marker and the read-only label — against the hand-built
 * `synthetic-floor-markers.html`, because no real capture of these floors exists yet. The fixture's
 * header explains what is invented; when a live capture lands, point this test at it.
 */
class FloorMarkersTest {

    private val html = Fixtures.load("synthetic-floor-markers.html")

    @Test
    fun `stacked badges keep the site's order`() {
        val body = requireNotNull(PostDetailParser.parse(html, postId = 999901L, page = 1).body)
        assertEquals(listOf("楼主", "服主", "管理", "管理(退休)"), body.badges)
    }

    @Test
    fun `edited marker is read from the header strip in either language`() {
        val detail = PostDetailParser.parse(html, postId = 999901L, page = 1)
        val body = requireNotNull(detail.body)
        assertTrue(body.isEdited)
        assertEquals("edited 5min ago", body.editedAtText)

        val punished = detail.comments.first { it.floor == "#1" }
        assertTrue(punished.isEdited)
        assertEquals("已编辑", punished.editedAtText)
        assertEquals(listOf("骗子", "违规禁止"), punished.badges)
    }

    @Test
    fun `the word edited inside a comment body is not a marker`() {
        val bystander =
            PostDetailParser
                .parse(html, postId = 999901L, page = 1)
                .comments
                .first { it.floor == "#2" }
        assertFalse(bystander.isEdited)
        assertNull(bystander.editedAtText)
        assertTrue(bystander.badges.isEmpty())
    }

    @Test
    fun `the red read-only label locks the row like the lock icon does`() {
        val page = PostListParser.parse(html, page = 1)
        val announcement = page.posts.first { it.postId == 999901L }
        val plain = page.posts.first { it.postId == 999902L }
        assertTrue(announcement.isLocked)
        assertFalse(plain.isLocked)
    }
}
