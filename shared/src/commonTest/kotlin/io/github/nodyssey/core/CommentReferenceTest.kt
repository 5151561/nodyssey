package io.github.nodyssey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentReferenceTest {
    @Test
    fun `同帖楼层链接支持绝对地址相对地址和跳转包装`() {
        listOf(
            "/post-42-2#11",
            "https://www.nodeseek.com/post-42-2#11",
            "https://nodeseek.com/post-42-2#11",
            "http://www.nodeseek.com:80/post-42-2#11",
            "https://www.nodeseek.com:443/post-42-2?source=quote#11",
            // 端口规则跟着 isOwnSiteUrl 走，与「这条链接开不开原生页」同一个答案；
            // 这里自己再写一套，就是本文件里第四套互相不一致的站点判断。
            "https://www.nodeseek.com:8443/post-42-2#11",
            "https://www.nodeseek.com/jump?to=https%3A%2F%2Fwww.nodeseek.com%2Fpost-42-2%2311",
        ).forEach { assertEquals(11, NodeSeekSite.referencedFloor(42, it), it) }
        assertEquals(0, NodeSeekSite.referencedFloor(42, "/post-42-1#0"))
    }

    @Test
    fun `跨帖外站和无效锚点不能跳到同号楼层`() {
        listOf(
            "/post-43-2#11",
            "https://example.com/post-42-2#11",
            "https://www.nodeseek.com.example.com/post-42-2#11",
            // 帖子路径下的其他页面，锚点不是它的楼层 —— 这里比 parsePostRoute 的 find 严是故意的。
            "/post-42-2/other#11",
            "/space/42#11",
            "/post-42-2",
            "/post-42-2#reply-11",
            "/post-42-2#-1",
            "/post-42-2#99999999999999999999",
            // 手写 markdown 里的裸锚点：解析时已被绝对化成站点根路径，没有帖子可认。
            // 楼层要从标签里取，见 PostDetailScreen 的 QuoteRef.openFrom。
            "#11",
        ).forEach { assertNull(NodeSeekSite.referencedFloor(42, it), it) }
    }
}
