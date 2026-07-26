package io.github.nsreader.ui.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** The toolbar's text transforms, including where the caret lands afterwards. */
class MarkdownEditingTest {
    @Test
    fun `bold wraps the selection and keeps it selected`() {
        val value = TextFieldValue("规则改名费用", TextRange(2, 4))

        val result = applyMarkdown(value, EditorAction.BOLD)

        assertEquals("规则**改名**费用", result.text)
        assertEquals(TextRange(4, 6), result.selection)
    }

    @Test
    fun `bold on an empty selection inserts a placeholder that the next keystroke replaces`() {
        val result = applyMarkdown(TextFieldValue("", TextRange(0)), EditorAction.BOLD)

        assertEquals("**加粗文字**", result.text)
        assertEquals(TextRange(2, 6), result.selection)
    }

    @Test
    fun `a link puts the caret in the empty address, not on the label`() {
        val result = applyMarkdown(TextFieldValue("看这个", TextRange(0, 3)), EditorAction.LINK)

        assertEquals("[看这个](https://)", result.text)
        assertEquals(TextRange("[看这个](https://".length), result.selection)
    }

    @Test
    fun `line prefixes toggle rather than stacking`() {
        val quoted = applyMarkdown(TextFieldValue("第二行", TextRange(1)), EditorAction.QUOTE)
        assertEquals("> 第二行", quoted.text)

        val unquoted = applyMarkdown(quoted, EditorAction.QUOTE)
        assertEquals("第二行", unquoted.text)
    }

    @Test
    fun `a line prefix applies to the caret's own line`() {
        val value = TextFieldValue("第一行\n第二行", TextRange(5))

        val result = applyMarkdown(value, EditorAction.LIST)

        assertEquals("第一行\n- 第二行", result.text)
    }

    @Test
    fun `an appended image starts its own block, and removing it leaves the body as it was`() {
        val image = "![a.png](https://cdn.nodeimage.com/i/a.webp)"

        val withImage = appendBlock("正文", image)

        assertEquals("正文\n\n$image", withImage)
        assertEquals("正文", removeBlock(withImage, image))
    }

    @Test
    fun `appending to an empty body does not open with blank lines`() {
        assertEquals("![a](u)", appendBlock("", "![a](u)"))
    }

    @Test
    fun `backspace removes a whole emoji rather than half of it`() {
        val value = TextFieldValue("赞一个🎉", TextRange("赞一个🎉".length))

        val result = value.deleteBackwards()

        assertEquals("赞一个", result.text)
    }

    @Test
    fun `backspace removes a variation-selector emoji in one tap`() {
        // ❤️ is U+2764 + U+FE0F; a code-point step would leave the black text-style heart behind.
        val value = TextFieldValue("好❤️", TextRange("好❤️".length))

        val result = value.deleteBackwards()

        assertEquals("好", result.text)
    }
}
