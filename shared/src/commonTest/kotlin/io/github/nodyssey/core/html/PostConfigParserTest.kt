package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The blob is built here rather than loaded from a captured page: every case below turns on one
 * field being present, absent or the wrong JSON type, and a 100 KB capture can only ever demonstrate
 * whichever combination that account happened to be in that day.
 */
class PostConfigParserTest {

    @Test
    fun `reads the thread's collection state off postData itself`() {
        val config = parse(postData = """"collected":true,"collectionCount":12,"comments":[]""")

        assertEquals(true, config.collected)
        assertEquals(12, config.collectionCount)
    }

    @Test
    fun `an uncollected thread is false rather than absent`() {
        val config = parse(postData = """"collected":false,"collectionCount":0,"comments":[]""")

        assertEquals(false, config.collected)
        assertEquals(0, config.collectionCount)
    }

    /**
     * The distinction the star depends on. A page with no blob has told us nothing, and offering to
     * remove a collection that may well exist is worse than offering nothing at all.
     */
    @Test
    fun `a page with no blob leaves the collection state unknown rather than false`() {
        val config = PostConfigParser.parse(Ksoup.parse("<html><body><div class='nsk-post'></div></body></html>"))

        assertNull(config.collected)
        assertNull(config.collectionCount)
    }

    @Test
    fun `a blob without the collection keys leaves them unknown`() {
        val config = parse(postData = """"comments":[]""")

        assertNull(config.collected)
        assertNull(config.collectionCount)
    }

    /** The site has sent tallies as both `12` and `"12"`; the count must survive either. */
    @Test
    fun `a collection count sent as a string still reads as a number`() {
        val config = parse(postData = """"collected":true,"collectionCount":"12","comments":[]""")

        assertEquals(12, config.collectionCount)
    }

    /**
     * Reshaping `comments` used to cost the whole blob, because the parser reached straight through
     * `postData` into it. The thread-level fields sit one level above and must not go with it.
     */
    @Test
    fun `a blob with no comments array still yields the thread-level state`() {
        val config = parse(postData = """"collected":true,"collectionCount":3""")

        assertEquals(true, config.collected)
        assertEquals(3, config.collectionCount)
        assertTrue(config.reactions.isEmpty())
    }

    @Test
    fun `still reads per-floor marks and block flags`() {
        val config =
            parse(
                postData =
                """
                "collected":false,
                "comments":[
                    {"commentId":1,"likeCount":2,"dislikeCount":0,"upvoteCount":5,
                     "liked":true,"disliked":false,"upvoted":false,"blocked":false},
                    {"commentId":2,"likeCount":0,"dislikeCount":1,"upvoteCount":0,
                     "liked":false,"disliked":false,"upvoted":true,"blocked":true}
                ]
                """.trimIndent(),
            )

        val first = requireNotNull(config.reactions[1L])
        assertEquals(2, first.likeCount)
        assertEquals(5, first.upvoteCount)
        assertTrue(first.liked)
        assertFalse(first.upvoted)

        val second = requireNotNull(config.reactions[2L])
        assertEquals(1, second.dislikeCount)
        assertTrue(second.upvoted)

        assertEquals(setOf(2L), config.blockedCommentIds)
    }

    /**
     * `poster.isMe` is the site's own answer to "did I write this", and the only thing that puts
     * 编辑 on a floor. A page with no blob claims nothing, which is the same as owning nothing.
     */
    @Test
    fun `reads which floors this account wrote`() {
        val config =
            parse(
                postData =
                """
                "comments":[
                  {"commentId":1,"poster":{"name":"a","isMe":true}},
                  {"commentId":2,"poster":{"name":"b","isMe":false}},
                  {"commentId":3,"poster":{"name":"c"}},
                  {"commentId":4}
                ]
                """.trimIndent(),
            )

        assertEquals(setOf(1L), config.ownCommentIds)
    }

    /** A reshaped `poster` must cost the ownership flag and nothing else on the page. */
    @Test
    fun `a poster that is not an object leaves the tallies intact`() {
        val config =
            parse(
                postData = """"comments":[{"commentId":1,"poster":"nssk","likeCount":3}]""",
            )

        assertTrue(config.ownCommentIds.isEmpty())
        assertEquals(3, requireNotNull(config.reactions[1L]).likeCount)
    }

    /** Unreadable JSON must not throw: the article is worth showing without the tallies. */
    @Test
    fun `a malformed blob yields an empty config`() {
        val html = pageWith(Base64.encode("not json".encodeToByteArray()))

        val config = PostConfigParser.parse(Ksoup.parse(html))

        assertNull(config.collected)
        assertTrue(config.reactions.isEmpty())
    }

    private fun parse(postData: String): PostConfig {
        val json = """{"user":{"member_id":52425},"postData":{$postData}}"""
        val encoded = Base64.encode(json.encodeToByteArray())
        return PostConfigParser.parse(Ksoup.parse(pageWith(encoded)))
    }

    private fun pageWith(encoded: String): String =
        """<html><body><script id="temp-script" type="text/template">$encoded</script></body></html>"""
}
