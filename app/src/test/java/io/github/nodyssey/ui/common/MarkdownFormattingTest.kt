package io.github.nodyssey.ui.common

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The caret arithmetic that used to exist twice — once in the post composer, once in the signature
 * editor — and was the thing most likely to drift when only one copy got fixed.
 */
class MarkdownFormattingTest {
    private val bold = MarkdownInsertion(prefix = "**", suffix = "**", placeholder = "加粗文字")
    private val heading = MarkdownInsertion(prefix = "## ", placeholder = "标题")
    private val link =
        MarkdownInsertion(
            prefix = "[",
            suffix = MARKDOWN_LINK_SUFFIX,
            placeholder = "链接文字",
            caretInSuffix = MARKDOWN_LINK_CARET,
        )

    private fun field(text: String, selection: IntRange? = null) =
        TextFieldValue(
            text = text,
            selection =
            selection?.let { TextRange(it.first, it.last) } ?: TextRange(text.length),
        )

    @Test
    fun `with nothing selected the placeholder is inserted and the caret sits on it`() {
        val result = field("").applyMarkdown(bold)

        assertEquals("**加粗文字**", result.text)
        // Just past the opening delimiter, so typing replaces the placeholder in place.
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun `a selection is wrapped and the caret goes to the end`() {
        val result = field("出杭州轻量", selection = 0..5).applyMarkdown(bold)

        assertEquals("**出杭州轻量**", result.text)
        assertEquals(TextRange("**出杭州轻量**".length), result.selection)
    }

    @Test
    fun `a prefix-only action needs no suffix`() {
        val result = field("关于我", selection = 0..3).applyMarkdown(heading)

        assertEquals("## 关于我", result.text)
        assertEquals(TextRange("## 关于我".length), result.selection)
    }

    /** Link is the exception: with the text written, the URL is the only thing left to type. */
    @Test
    fun `link puts the caret in the url slot rather than at the end`() {
        val result = field("星辰担保", selection = 0..4).applyMarkdown(link)

        assertEquals("[星辰担保](https://)", result.text)
        assertEquals(TextRange("[星辰担保](".length), result.selection)
    }

    @Test
    fun `an empty link selection puts the caret on the link text`() {
        val result = field("").applyMarkdown(link)

        assertEquals("[链接文字](https://)", result.text)
        assertEquals(TextRange(1), result.selection)
    }

    @Test
    fun `formatting applies in the middle of existing text`() {
        val result = field("交易走星辰担保，勿私", selection = 3..7).applyMarkdown(bold)

        assertEquals("交易走**星辰担保**，勿私", result.text)
        assertEquals(TextRange("交易走**星辰担保**".length), result.selection)
    }

    @Test
    fun `an out-of-range selection is clamped rather than crashing`() {
        val result =
            TextFieldValue("短", selection = TextRange(0, 99)).applyMarkdown(bold)

        assertEquals("**短**", result.text)
    }
}
