package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.appendBlock
import io.github.plaza.designsys.editor.applyMarkdown
import io.github.plaza.designsys.editor.deleteBackwards
import io.github.plaza.designsys.editor.removeBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/** The toolbar's text transforms, including where the caret lands afterwards. */
class MarkdownEditingTest {
    private fun field(text: String, selection: TextRange) =
        TextFieldState(initialText = text, initialSelection = selection)

    @Test
    fun `bold wraps the selection and keeps it selected`() {
        val state = field("规则改名费用", TextRange(2, 4))

        state.edit { applyMarkdown(EditorAction.BOLD) }

        assertEquals("规则**改名**费用", state.text.toString())
        assertEquals(TextRange(4, 6), state.selection)
    }

    @Test
    fun `bold on an empty selection inserts a placeholder that the next keystroke replaces`() {
        val state = field("", TextRange(0))

        state.edit { applyMarkdown(EditorAction.BOLD) }

        assertEquals("**加粗文字**", state.text.toString())
        assertEquals(TextRange(2, 6), state.selection)
    }

    @Test
    fun `a link puts the caret in the empty address, not on the label`() {
        val state = field("看这个", TextRange(0, 3))

        state.edit { applyMarkdown(EditorAction.LINK) }

        assertEquals("[看这个](https://)", state.text.toString())
        assertEquals(TextRange("[看这个](https://".length), state.selection)
    }

    /** Unlike the signature editor's link, which stops before the scheme. Both are deliberate. */
    @Test
    fun `an empty link selection still lands in the address`() {
        val state = field("", TextRange(0))

        state.edit { applyMarkdown(EditorAction.LINK) }

        assertEquals("[链接文字](https://)", state.text.toString())
        assertEquals(TextRange("[链接文字](https://".length), state.selection)
    }

    @Test
    fun `line prefixes toggle rather than stacking`() {
        val state = field("第二行", TextRange(1))

        state.edit { applyMarkdown(EditorAction.QUOTE) }
        assertEquals("> 第二行", state.text.toString())

        state.edit { applyMarkdown(EditorAction.QUOTE) }
        assertEquals("第二行", state.text.toString())
    }

    @Test
    fun `a line prefix applies to the caret's own line`() {
        val state = field("第一行\n第二行", TextRange(5))

        state.edit { applyMarkdown(EditorAction.LIST) }

        assertEquals("第一行\n- 第二行", state.text.toString())
    }

    @Test
    fun `an appended image starts its own block, and removing it leaves the body as it was`() {
        val image = "![a.png](https://cdn.nodeimage.com/i/a.webp)"
        val state = field("正文", TextRange(2))

        state.edit { appendBlock(image) }
        assertEquals("正文\n\n$image", state.text.toString())

        state.edit { removeBlock(image) }
        assertEquals("正文", state.text.toString())
    }

    @Test
    fun `appending to an empty body does not open with blank lines`() {
        val state = field("", TextRange(0))

        state.edit { appendBlock("![a](u)") }

        assertEquals("![a](u)", state.text.toString())
    }

    /** The bug this migration fixed: an upload landing mid-sentence used to yank the caret away. */
    @Test
    fun `appending an image leaves the caret where the user was typing`() {
        val state = field("正在写的一句话", TextRange(3))

        state.edit { appendBlock("![a](u)") }

        assertEquals(TextRange(3), state.selection)
    }

    @Test
    fun `backspace removes a whole emoji rather than half of it`() {
        val state = field("赞一个🎉", TextRange("赞一个🎉".length))

        state.edit { deleteBackwards() }

        assertEquals("赞一个", state.text.toString())
    }

    @Test
    fun `backspace removes a variation-selector emoji in one tap`() {
        // ❤️ is U+2764 + U+FE0F; a code-point step would leave the black text-style heart behind.
        val state = field("好❤️", TextRange("好❤️".length))

        state.edit { deleteBackwards() }

        assertEquals("好", state.text.toString())
    }
}
