package io.github.nodyssey.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The Markdown the editor loads, out of the same `__config__` blob the tallies come from.
 *
 * Built here rather than captured for the reason [PostConfigParserTest] gives: every case turns on
 * one field being present, absent or the wrong shape.
 */
class PostSourceParserTest {

    @Test
    fun `reads the thread fields and one floor's markdown`() {
        val source =
            requireNotNull(
                parse(
                    """
                    "title":"VMISS 9929 TCP 调优记录","rank":1,
                    "comments":[
                      {"commentId":9,"floorIndex":0,"markdown":"<details><summary>前</summary>\\n正文\\n</details>","poster":{"isMe":true}},
                      {"commentId":10,"floorIndex":1,"markdown":"沙发","poster":{"isMe":false}}
                    ]
                    """.trimIndent(),
                ),
            )

        assertEquals("VMISS 9929 TCP 调优记录", source.title)
        assertEquals(1, source.rank)

        val opening = requireNotNull(source.floor(9))
        assertTrue(opening.isOpeningPost)
        assertTrue(opening.isMine)
        // Verbatim, folds and all: this is the text that goes back on the wire, not a re-render.
        assertTrue(opening.markdown.startsWith("<details><summary>前</summary>"))

        val reply = requireNotNull(source.floor(10))
        assertFalse(reply.isOpeningPost)
        assertFalse(reply.isMine)
        assertEquals("沙发", reply.markdown)
    }

    /** No blob is how a post page looks signed out, and a signed-out reader can edit nothing. */
    @Test
    fun `a page with no blob has no source`() {
        assertNull(PostSourceParser.parse("<html><body><div class='nsk-post'></div></body></html>", postId = 1))
    }

    @Test
    fun `a malformed blob has no source`() {
        val encoded = Base64.getEncoder().encodeToString("not json".toByteArray(StandardCharsets.UTF_8))
        assertNull(PostSourceParser.parse(pageWith(encoded), postId = 1))
    }

    /** A floor whose `markdown` never arrived is not editable, and must not read as an empty post. */
    @Test
    fun `a floor without markdown is left out entirely`() {
        val source = requireNotNull(parse(""""title":"t","comments":[{"commentId":9,"floorIndex":0}]"""))

        assertTrue(source.floors.isEmpty())
        assertNull(source.floor(9))
    }

    /** 阅读权限 has to be sent back on every 主楼 save; a blob without it means 公开. */
    @Test
    fun `a blob without a rank reads as public`() {
        val source = requireNotNull(parse(""""title":"t","comments":[]"""))

        assertEquals(0, source.rank)
    }

    private fun parse(postData: String) =
        PostSourceParser.parse(
            html =
            pageWith(
                Base64.getEncoder().encodeToString(
                    """{"user":{"member_id":52425},"postData":{$postData}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                ),
            ),
            postId = 876332,
        )

    private fun pageWith(encoded: String): String =
        """<html><body><script id="temp-script" type="text/template">$encoded</script></body></html>"""
}
