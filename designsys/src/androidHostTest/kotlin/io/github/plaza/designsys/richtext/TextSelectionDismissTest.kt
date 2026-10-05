package io.github.plaza.designsys.richtext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A selection in a post body is put down by a tap anywhere, and its toolbar leaves as it was.
 *
 * The second half is the 全选 that flashed on every dismissal: clearing a selection drops 复制 from
 * the menu's data while the toolbar is still open, and the platform redraws it with 全选 alone before
 * it goes. What the platform toolbar would draw is whatever the data provider it was handed answers,
 * so that is what is read here, after the selection is gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// API 27: from 28 on a selection drag shows the platform Magnifier, whose dismiss() throws under
// Robolectric (no Surface behind its popup). The menu and the gestures under test are the same there.
@Config(qualifiers = "w360dp-h800dp", sdk = [27])
class TextSelectionDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val toolbar = RecordingToolbar()

    private fun setContent() {
        composeRule.setContent {
            PlazaTheme {
                CompositionLocalProvider(
                    LocalTextContextMenuToolbarProvider provides toolbar,
                    LocalTextContextMenuDropdownProvider provides RecordingToolbar(),
                ) {
                    DismissTextSelectionOnTap {
                        Column {
                            RichContent(
                                nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Text(BODY)))),
                                onLinkClick = {},
                                onImageClick = {},
                            )
                            Box(Modifier.fillMaxWidth().height(200.dp).testTag(ELSEWHERE))
                        }
                    }
                }
            }
        }
    }

    private fun keys(): List<Any> =
        toolbar.dataProvider!!.data().components.filterIsInstance<TextContextMenuItem>().map { it.key }

    @Test
    fun `a tap outside the body puts the selection down and the toolbar leaves with copy still on it`() {
        setContent()
        composeRule.onNodeWithText(BODY).performTouchInput { longClick(center) }
        composeRule.waitForIdle()
        assertTrue("long press did not open the toolbar", toolbar.open)
        assertTrue(TextContextMenuKeys.CopyKey in keys())

        composeRule.onNodeWithTag(ELSEWHERE).performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertFalse("tap outside the body left the selection up", toolbar.open)
        assertTrue("the closing toolbar lost 复制", TextContextMenuKeys.CopyKey in keys())
    }

    @Test
    fun `a long press elsewhere is not a tap and does not take the selection down`() {
        setContent()
        composeRule.onNodeWithText(BODY).performTouchInput { longClick(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ELSEWHERE).performTouchInput { longClick(center) }
        composeRule.waitForIdle()

        assertTrue(toolbar.open)
    }

    private class RecordingToolbar : TextContextMenuProvider {
        var dataProvider: TextContextMenuDataProvider? = null
        var open = false

        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            this.dataProvider = dataProvider
            open = true
            try {
                awaitCancellation()
            } finally {
                open = false
            }
        }
    }

    private companion object {
        const val BODY = "手机号注册的，开始要验证真人了"
        const val ELSEWHERE = "elsewhere"
    }
}
