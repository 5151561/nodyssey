package io.github.nodyssey.data.offline

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which pictures a downloaded floor has to bring with it.
 *
 * The walk into wrappers is the whole test. Posts on this site put their screenshots inside 折叠
 * blocks and table cells, and a copy that only kept the top-level ones would look complete right up
 * until the reader opened the fold offline — which is the one moment it cannot be fixed.
 */
class OfflineImagesTest {
    @Test
    fun `pictures inside folds, quotes, tabs, lists and table cells all come along`() {
        val content =
            floor(
                RichNode.BlockImage("https://i/1.png", alt = null),
                RichNode.Fold("看图", listOf(RichNode.BlockImage("https://i/2.png", alt = null))),
                RichNode.Quote(listOf(RichNode.BlockImage("https://i/3.png", alt = null))),
                RichNode.Tabs(listOf(RichNode.Tabs.Tab("一", listOf(RichNode.BlockImage("https://i/4.png", null))))),
                RichNode.ListBlock(ordered = false, items = listOf(listOf(RichNode.BlockImage("https://i/5.png", null)))),
                RichNode.Table(cells = listOf(listOf(listOf(InlineNode.Image("https://i/6.png", alt = null))))),
                RichNode.Paragraph(listOf(InlineNode.Sticker("https://i/7.png", alt = null))),
            )

        assertEquals((1..7).map { "https://i/$it.png" }.toSet(), content.imageUrls())
    }

    /** The author's picture is part of the floor; a stored thread of blank circles is not the thread. */
    @Test
    fun `the avatar counts, and so does a signature's picture`() {
        val content =
            floor(RichNode.Paragraph(listOf(InlineNode.Text("hello"))))
                .copy(
                    avatarUrl = "https://i/avatar.png",
                    signatureNodes = listOf(RichNode.BlockImage("https://i/sig.png", alt = null)),
                )

        assertEquals(setOf("https://i/avatar.png", "https://i/sig.png"), content.imageUrls())
    }

    /**
     * A generated avatar is served as a `data:` URI on some accounts, and the site writes relative
     * paths in places. Neither is something to go and fetch.
     */
    @Test
    fun `anything that is not an http address is left alone`() {
        val content =
            floor(RichNode.BlockImage("/static/placeholder.png", alt = null))
                .copy(avatarUrl = "data:image/png;base64,AAAA")

        assertEquals(emptySet<String>(), content.imageUrls())
    }

    private fun floor(vararg nodes: RichNode) =
        PostContent(
            commentId = 1,
            floor = null,
            authorName = "tester",
            authorUid = 1,
            avatarUrl = null,
            isOriginalPoster = false,
            badges = emptyList(),
            createdAtText = null,
            createdAtTitle = null,
            categoryTitle = null,
            nodes = nodes.toList(),
        )
}
