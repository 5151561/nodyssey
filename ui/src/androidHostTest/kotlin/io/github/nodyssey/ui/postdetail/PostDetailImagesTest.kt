package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The image viewer pages through whatever this returns, so its order *is* the "2 / 4" the user reads.
 */
class PostDetailImagesTest {
    private fun content(
        vararg nodes: RichNode,
        blocked: Boolean = false,
    ) = PostContent(
        commentId = null,
        floor = null,
        authorName = "tester",
        authorUid = null,
        avatarUrl = null,
        isOriginalPoster = false,
        badges = emptyList(),
        createdAtText = null,
        createdAtTitle = null,
        categoryTitle = null,
        nodes = nodes.toList(),
        isBlocked = blocked,
    )

    private fun image(url: String) = RichNode.BlockImage(url = url, alt = null)

    @Test
    fun `collects images from the body then the comments in reading order`() {
        val state =
            PostDetailUiState(
                body = content(image("a.png"), RichNode.Divider, image("b.png")),
                comments = listOf(content(image("c.png")), content(image("d.png"))),
            )

        assertEquals(listOf("a.png", "b.png", "c.png", "d.png"), state.imageUrls())
    }

    @Test
    fun `reaches images nested in quotes and lists`() {
        val state =
            PostDetailUiState(
                body =
                content(
                    RichNode.Quote(children = listOf(image("quoted.png"))),
                    RichNode.ListBlock(ordered = false, items = listOf(listOf(image("listed.png")))),
                ),
            )

        assertEquals(listOf("quoted.png", "listed.png"), state.imageUrls())
    }

    /**
     * `/post-287967-1`: screenshots filed in a layout table. Left out of this list, a tapped
     * thumbnail was "not in the list" and the viewer opened page one — the post's first badge.
     */
    @Test
    fun `reaches images inside table cells and tabs`() {
        val state =
            PostDetailUiState(
                body =
                content(
                    RichNode.Table(
                        cells = listOf(
                            listOf(listOf(InlineNode.Text("IPv4")), listOf(InlineNode.Text("IPv6"))),
                            listOf(
                                listOf(InlineNode.Image("v4.png", null)),
                                listOf(InlineNode.Image("v6.png", null)),
                            ),
                        ),
                    ),
                    RichNode.Tabs(
                        tabs = listOf(RichNode.Tabs.Tab(title = "一", children = listOf(image("tabbed.png")))),
                    ),
                ),
            )

        assertEquals(listOf("v4.png", "v6.png", "tabbed.png"), state.imageUrls())
    }

    /**
     * `/post-910421-1`: a 测评 whose 三网测速 screenshots all sit inside 折叠. Missing from this list,
     * every one of them opened the viewer on the post's first tabbed image instead of itself — the
     * fold's own six pictures could not be reached at all. Closed folds count too: the reader may
     * open one at any time, and the list is built once for the whole thread.
     */
    @Test
    fun `reaches images inside a fold`() {
        val state =
            PostDetailUiState(
                body =
                content(
                    image("outside.png"),
                    RichNode.Fold(
                        title = "三网测速",
                        children = listOf(image("telecom.png"), image("unicom.png")),
                        open = false,
                    ),
                ),
            )

        assertEquals(listOf("outside.png", "telecom.png", "unicom.png"), state.imageUrls())
    }

    /** A screenshot quoted by three people is one image, or the page count would lie. */
    @Test
    fun `keeps one entry per url`() {
        val state =
            PostDetailUiState(
                body = content(image("same.png")),
                comments = listOf(content(image("same.png")), content(image("other.png"))),
            )

        assertEquals(listOf("same.png", "other.png"), state.imageUrls())
    }

    @Test
    fun `ignores inline stickers`() {
        val state =
            PostDetailUiState(
                body =
                content(
                    RichNode.Paragraph(
                        inlines =
                        listOf(
                            InlineNode.Text("哈哈"),
                            InlineNode.Sticker(url = "sticker.gif", alt = "笑"),
                        ),
                    ),
                    image("real.png"),
                ),
            )

        assertEquals(listOf("real.png"), state.imageUrls())
    }

    /**
     * A collapsed floor is collapsed in the viewer too. Paging into a blocked author's screenshot
     * would show the reader exactly the thing the block was for.
     */
    @Test
    fun `skips floors the site marked blocked`() {
        val state =
            PostDetailUiState(
                body = content(image("body.png")),
                comments =
                listOf(
                    content(image("blocked.png"), blocked = true),
                    content(image("ordinary.png")),
                ),
            )

        assertEquals(listOf("body.png", "ordinary.png"), state.imageUrls())
    }

    @Test
    fun `includes them once the reveal switch is on`() {
        val state =
            PostDetailUiState(
                comments = listOf(content(image("blocked.png"), blocked = true)),
                showBlockedContent = true,
            )

        assertEquals(listOf("blocked.png"), state.imageUrls())
    }
}
