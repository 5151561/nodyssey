package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The avatar's bottom edge lands on the bottom of the meta line's tag.
 *
 * That is the whole definition of [listAvatarSize], and it is a claim about a thread row's
 * geometry — the title's line box, the 4dp gap, the tag's pill — not about any particular number of
 * dp. Which is also why it holds under Robolectric's stub font: the size is measured from the same
 * font the row is laid out in, so a stub that gets `lineHeight` wrong gets it wrong on both sides.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ListAvatarSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the avatar bottom sits on the tag bottom for a one-line title`() {
        composeRule.setContent {
            PlazaTheme {
                // A thread row's geometry: the avatar on the title's cap line, the title over its
                // meta line of tags, 4dp apart.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .offset(y = AvatarCapOffset)
                            .size(listAvatarSize())
                            .semantics { contentDescription = AVATAR },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ThreadRowTitle(AnnotatedString("一行标题"))
                        TonalTag(
                            text = "日常",
                            containerColor = Color.Red,
                            contentColor = Color.White,
                            modifier = Modifier.semantics { contentDescription = TAG },
                        )
                    }
                }
            }
        }

        assertEquals(bottomOf(TAG), bottomOf(AVATAR), 0.5f)
    }

    private fun bottomOf(description: String): Float =
        composeRule
            .onNodeWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom

    private companion object {
        const val AVATAR = "avatar"
        const val TAG = "tag"
    }
}
