package io.github.nodyssey.ui.postlist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A feed card's two meta lines each stay one line, and everything on them is centred on it: the board
 * tag and the time under the author's name, the counts at the foot.
 *
 * This is the bug the icons were introduced for: spelled out as `1234 浏览` and `12 回复`, the counts
 * ran past the width of a 360dp phone as soon as the author had a real name, and the items that
 * overflowed dropped onto a second row. Half the metadata sitting a line lower is what "nothing lines
 * up" looks like from the outside.
 *
 * Asserted on the vertical *centre* rather than the top edge, because the items genuinely differ in
 * height — the board tag is a filled pill, the counts are icon-and-number pairs — and it is the
 * centre that the card's rows align. Positions decided by layout rather than by glyph
 * metrics, so Robolectric's stub font (which does not honour `lineHeight`) cannot skew them; the
 * stub font is in fact wider than a real one here, which makes the wrapping check strictly harsher
 * than a device would be.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostRowMetaLineTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val longAuthor = "一个很长的用户名字abcdef"

    @Test
    fun `the meta line stays on one line even with a long author name`() {
        composeRule.setContent {
            PlazaTheme {
                PostRow(post = post(), onClick = {})
            }
        }

        val header = listOf("board" to centreOfText("日常"), "time" to centreOfText("3小时前"))
        val foot = listOf("reply" to centreOfLabel("12 回复"), "view" to centreOfLabel("1234 浏览"))

        // 1dp of slack: these are pixel centres of boxes with odd heights, not a design tolerance.
        fun List<Pair<String, Float>>.offLine() = filter { (_, centre) -> kotlin.math.abs(centre - first().second) > 1f }
        assertEquals(emptyList<Pair<String, Float>>(), header.offLine())
        assertEquals(emptyList<Pair<String, Float>>(), foot.offLine())
    }

    private fun centreOfText(text: String): Float =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y

    private fun centreOfLabel(label: String): Float =
        composeRule
            .onNodeWithContentDescription(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y

    private fun post() =
        FeedPost(
            summary =
            PostSummary(
                postId = 1,
                title = "一个标题",
                authorName = longAuthor,
                authorUid = 1,
                avatarUrl = null,
                categoryTitle = "日常",
                categorySlug = "daily",
                viewCount = 1234,
                commentCount = 12,
                lastActiveText = "3小时前",
                lastActiveTitle = null,
                isPinned = false,
                isLocked = false,
                isAwarded = false,
            ),
            isRead = false,
            newCommentCount = 0,
            page = null,
        )
}
