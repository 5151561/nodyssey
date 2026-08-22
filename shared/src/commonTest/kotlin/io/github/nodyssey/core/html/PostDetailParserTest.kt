package io.github.nodyssey.core.html

import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostDetailParserTest {

    private val detail =
        PostDetailParser.parse(Fixtures.load("post-703863-1.html"), postId = 703863L, page = 1)

    /** Page 1 always carries the opening post; a null here is itself a parser failure. */
    private val body = requireNotNull(detail.body)

    @Test
    fun `parses the post header`() {
        assertEquals("绿云抢鸡竞赛又要开始了，一波传家宝又要来袭", detail.title)
        assertEquals("ipv4", body.authorName)
        assertEquals(34378L, body.authorUid)
        assertEquals("#0", body.floor)
        assertEquals("日常", body.categoryTitle)
        assertEquals("2026-04-27 15:57:00", body.createdAtTitle)
        assertTrue(body.isOriginalPoster)
    }

    @Test
    fun `separates the attachment image from the inline stickers`() {
        val blockImages = body.nodes.filterIsInstance<RichNode.BlockImage>()
        assertEquals(1, blockImages.size)
        assertEquals(
            "https://cdn.nodeimage.com/i/FBICDKhbCSRbkZ5Kx5qyiyit927K3vCg.webp",
            blockImages.first().url,
        )

        val stickers = body.nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Sticker>()
        assertEquals(3, stickers.size)
        assertTrue(stickers.all { it.url.startsWith("https://www.nodeseek.com/static/image/sticker/") })
    }

    @Test
    fun `keeps the text that follows inline stickers`() {
        val text = body.nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Text>()
            .joinToString("") { it.text }
        assertTrue(text.contains("喊上你的五指小姐姐一起抢吧"))
    }

    @Test
    fun `parses the comment list`() {
        assertTrue(detail.comments.isNotEmpty())
        val first = detail.comments.first()
        assertEquals("ggbeng", first.authorName)
        assertEquals(24520L, first.authorUid)
        assertEquals("#4", first.floor)
        assertEquals(9727591L, first.commentId)
        assertFalse(first.isOriginalPoster)
        assertTrue(first.badges.isEmpty())
    }

    @Test
    fun `parses public signatures below comments`() {
        assertTrue(body.signatureNodes.isEmpty())

        val signature = detail.comments.first { it.authorName == "ggbeng" }.signatureNodes
        val links = signature
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Link>()

        assertTrue(links.any { it.text == "个人博客" && it.url == "https://ggbeng.tech" })
    }

    @Test
    fun `reads the header badges`() {
        assertEquals(listOf("楼主"), body.badges)

        val opReply = detail.comments.first { it.isOriginalPoster }
        assertEquals(listOf("楼主"), opReply.badges)

        // The role-tag structure differs from is-poster (text sits in a nested span) — both parse.
        val dev = detail.comments.first { it.authorName == "ggchaos" }
        assertEquals(listOf("Dev"), dev.badges)
    }

    @Test
    fun `floors without an edited marker read as unedited`() {
        assertFalse(body.isEdited)
        assertTrue(detail.comments.none { it.isEdited })
    }

    @Test
    fun `turns br separated lines into line breaks rather than lost text`() {
        val inlines = detail.comments.first().nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
        assertTrue(inlines.any { it is InlineNode.LineBreak })
        val text = inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        assertTrue(text.contains("如果你是建站"))
        assertTrue(text.contains("如果你喜欢绿帽"))
    }

    @Test
    fun `reads the pager`() {
        assertEquals(1, detail.page)
        assertTrue(detail.totalPages >= 4)
        assertTrue(detail.hasNextPage)
    }

    @Test
    fun `reads the total from the elided last-page shortcut on a long thread`() {
        // A long thread's pager renders the far end as `<span class="ellipsis">..</span>37`,
        // whose text is not a number — the total must come from the href instead.
        val html =
            """
            <html><body>
            <div class="nsk-pager post-top-pager"><div role="navigation">
              <span href="/post-1-1" class="pager-pos pager-cur">1</span>
              <a href="/post-1-2" class="pager-pos">2</a>
              <a href="/post-1-5" class="pager-pos">5</a>
              <a href="/post-1-37" class="pager-pos"><span class="ellipsis">..</span>37</a>
              <a href="/post-1-2" rel="next" class="pager-next"></a>
            </div></div>
            </body></html>
            """.trimIndent()
        val parsed = PostDetailParser.parse(html, postId = 1L, page = 1)
        assertEquals(37, parsed.totalPages)
    }

    /**
     * The tallies are not in the markup — they ride in the page's base64 `__config__` blob, keyed by
     * the same comment id the floor's `data-comment-id` carries. This floor is a real one from the
     * captured page, which is why the numbers are uneven: three chicken legs, one upvote, no dislike.
     */
    @Test
    fun `reads reaction tallies out of the page config`() {
        val floor = detail.comments.first { it.commentId == 9727591L }
        val reactions = requireNotNull(floor.reactions)
        assertEquals(3, reactions.likeCount)
        assertEquals(1, reactions.upvoteCount)
        assertEquals(0, reactions.dislikeCount)
        // The capture was taken by an account that had spent nothing on this floor.
        assertFalse(reactions.liked)
        assertFalse(reactions.upvoted)
    }

    /**
     * The opening post is a floor like any other — NodeSeek keeps it as `comments[0]` with its own
     * comment id — so it has to come out with tallies too, or the buttons under the post itself
     * would be the one place in the thread that never works.
     */
    @Test
    fun `gives the opening post its own tallies`() {
        assertEquals(9727545L, body.commentId)
        assertEquals(0, requireNotNull(body.reactions).likeCount)
    }

    /**
     * A signed-out read has no blob, and zeroes would be a claim the page never made — "nobody has
     * upvoted this" instead of "we were not told". The article still has to survive it.
     */
    @Test
    fun `reports no tallies at all when the page carries no config`() {
        val stripped =
            Fixtures.load("post-703863-1.html")
                .replace(Regex("""<script id="temp-script"[^>]*>[^<]*</script>"""), "")
        val parsed = PostDetailParser.parse(stripped, postId = 703863L, page = 1)

        assertNull(requireNotNull(parsed.body).reactions)
        assertTrue(parsed.comments.isNotEmpty())
        assertTrue(parsed.comments.all { it.reactions == null })
        // The point of the null: losing the tallies must not lose the thread.
        assertTrue(requireNotNull(parsed.body).nodes.isNotEmpty())
    }

    /**
     * `blocked` rides in the same `__config__` blob as the tallies, one entry per floor. Reading it
     * is the only way the app learns that a floor it was *sent* is one the account asked not to see.
     */
    @Test
    fun `marks the floors the config blob says are blocked`() {
        val target = requireNotNull(detail.comments.first().commentId)
        val config = """{"postData":{"comments":[{"commentId":$target,"blocked":true}]}}"""
        val parsed =
            PostDetailParser.parse(withConfig(config), postId = 703863L, page = 1)

        assertTrue(parsed.comments.first { it.commentId == target }.isBlocked)
        assertFalse(requireNotNull(parsed.body).isBlocked)
        assertTrue(parsed.comments.count { it.isBlocked } == 1)
    }

    /** The markup says it too, as `class="blocked-comment"`, and that is enough on its own. */
    @Test
    fun `marks a floor the markup blocked without a config blob`() {
        val html =
            """
            <html><body><ul class="comments">
              <li class="content-item blocked-comment" data-comment-id="5">
                <a class="floor-link">#1</a><a class="author-name" href="/space/1">someone</a>
                <article class="post-content"><p>hidden</p></article>
              </li>
              <li class="content-item" data-comment-id="6">
                <a class="floor-link">#2</a><a class="author-name" href="/space/2">other</a>
                <article class="post-content"><p>shown</p></article>
              </li>
            </ul></body></html>
            """.trimIndent()

        val parsed = PostDetailParser.parse(html, postId = 1L, page = 1)

        assertTrue(parsed.comments.first().isBlocked)
        assertFalse(parsed.comments.last().isBlocked)
    }

    @Test
    fun `leaves an ordinary thread unblocked`() {
        assertFalse(body.isBlocked)
        assertTrue(detail.comments.none { it.isBlocked })
    }

    /**
     * The blob names the moment; the markup marker only ever says that an edit happened. Both halves
     * matter to the reader — "编辑于 5min ago" next to a floor posted three hours back is the whole
     * point of the marker.
     */
    @Test
    fun `takes the edit time from the config blob`() {
        val target = requireNotNull(detail.comments.first().commentId)
        val config =
            """
            {"postData":{"comments":[{"commentId":$target,"time":{
              "createdDateRel":"36min ago","editedDate":"2026-04-27T08:20:00.000Z",
              "editedDateFormated":"2026-04-27 16:20:00","editedDateRel":"5min ago"}}]}}
            """.trimIndent()

        val parsed = PostDetailParser.parse(withConfig(config), postId = 703863L, page = 1)
        val edited = parsed.comments.first { it.commentId == target }

        assertTrue(edited.isEdited)
        assertEquals("5min ago", edited.editedAtText)
        assertEquals("2026-04-27 16:20:00", edited.editedAtTitle)
        assertTrue(parsed.comments.count { it.isEdited } == 1)
        assertFalse(requireNotNull(parsed.body).isEdited)
    }

    /** An unedited floor is what the fixture's own blob is full of: `editedDate` arrives null. */
    @Test
    fun `leaves an unedited floor unmarked`() {
        assertFalse(body.isEdited)
        assertNull(body.editedAtText)
        assertNull(body.editedAtTitle)
    }

    /** Swaps the fixture's bootstrap blob for [json], base64 as the site encodes it. */
    private fun withConfig(json: String): String {
        val encoded = kotlin.io.encoding.Base64.encode(json.encodeToByteArray())
        return Fixtures
            .load("post-703863-1.html")
            .replace(
                Regex("""<script id="temp-script"[^>]*>[^<]*</script>"""),
                """<script id="temp-script">$encoded</script>""",
            )
    }

    /**
     * The 加精 marker on a post page is a gold corner over the post wrapper, and the fixture thread is
     * not awarded — so the flag has to come out false rather than null, which is what a page carrying
     * the opening post is entitled to say.
     */
    @Test
    fun `reports an ordinary thread as not 推荐阅读`() {
        assertEquals(false, detail.isAwarded)
    }

    @Test
    fun `reads the 推荐阅读 corner off the post wrapper`() {
        val html =
            """
            <html><body><div class="nsk-post-wrapper">
              <div class="award-corner"><div title="推荐阅读" class="corner-triangle"></div></div>
              <div class="nsk-post"><div class="content-item" data-comment-id="1">
                <article class="post-content"><p>加精了的正文</p></article>
              </div></div>
            </div></body></html>
            """.trimIndent()

        assertEquals(true, PostDetailParser.parse(html, postId = 1L, page = 1).isAwarded)
    }

    /**
     * Page 2 renders the same wrapper without the corner (verified live 2026-08-17), so its silence
     * must read as "this page did not say" — reporting false would unmark the thread on screen as the
     * reader scrolls into it.
     */
    @Test
    fun `says nothing about 推荐阅读 on a page that carries no opening post`() {
        val html =
            """
            <html><body><div class="nsk-post-wrapper">
              <div class="nsk-post"></div>
              <ul class="comments"><li class="content-item" data-comment-id="2">
                <article class="post-content"><p>三楼</p></article>
              </li></ul>
            </div></body></html>
            """.trimIndent()

        val parsed = PostDetailParser.parse(html, postId = 1L, page = 2)
        assertNull(parsed.body)
        assertNull(parsed.isAwarded)
    }

    @Test
    fun `parses a long post without dropping content`() {
        val long =
            PostDetailParser.parse(Fixtures.load("post-705039-1.html"), postId = 705039L, page = 1)
        assertTrue(long.title.isNotBlank())
        assertTrue(requireNotNull(long.body).nodes.isNotEmpty())
        assertTrue(long.comments.isNotEmpty())
        long.comments.forEach { comment ->
            assertTrue(comment.authorName.isNotBlank(), "comment without author")
        }
    }
}
