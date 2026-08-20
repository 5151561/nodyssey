package io.github.nodyssey.ui.postlist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
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
 * 「字号」 moves the whole list row, not only the post bodies it used to move.
 *
 * The setting scaled the body roles alone, so the feed — the screen a reader spends the most time on
 * — was the one screen it did nothing to. Asserted on the `sp` each piece is actually laid out at
 * rather than on measured heights, which is both exact and immune to Robolectric's stub font.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ReadingSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the reading size moves the title, the meta line and the board tag`() {
        composeRule.setContent {
            PlazaTheme(fontScale = 1.5f) {
                PostRow(post = post(), onClick = {})
            }
        }

        // 15sp title, 12sp meta, 11sp tag, all at one and a half.
        assertEquals(22.5f, fontSizeOf("一个标题"), 0.01f)
        assertEquals(18f, fontSizeOf("某人"), 0.01f)
        assertEquals(16.5f, fontSizeOf("日常"), 0.01f)
    }

    private fun fontSizeOf(text: String): Float {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().layoutInput.style.fontSize.value
    }

    private fun post() =
        FeedPost(
            summary =
            PostSummary(
                postId = 1,
                title = "一个标题",
                authorName = "某人",
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
