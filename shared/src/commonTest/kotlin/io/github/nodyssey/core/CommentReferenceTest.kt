package io.github.nodyssey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentReferenceTest {
    @Test
    fun `同帖楼层链接支持绝对地址相对地址和锚点`() {
        listOf(
            "/post-42-2#11",
            "https://www.nodeseek.com/post-42-2#11",
            "https://nodeseek.com/post-42-2#11",
            "http://www.nodeseek.com:80/post-42-2#11",
            "https://www.nodeseek.com:443/post-42-2?source=quote#11",
            "#11",
        ).forEach { assertEquals(11, NodeSeekSite.referencedFloor(42, it), it) }
        assertEquals(0, NodeSeekSite.referencedFloor(42, "/post-42-1#0"))
    }

    @Test
    fun `跨帖外站和无效锚点不能跳到同号楼层`() {
        listOf(
            "/post-43-2#11",
            "https://example.com/post-42-2#11",
            "https://www.nodeseek.com.example.com/post-42-2#11",
            "https://www.nodeseek.com:8443/post-42-2#11",
            "/post-42-2/other#11",
            "/space/42#11",
            "/post-42-2",
            "/post-42-2#reply-11",
            "/post-42-2#-1",
            "/post-42-2#99999999999999999999",
        ).forEach { assertNull(NodeSeekSite.referencedFloor(42, it), it) }
    }
}
