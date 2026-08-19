package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

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
        TextFieldState(
            initialText = text,
            initialSelection =
            selection?.let { TextRange(it.first, it.last) } ?: TextRange(text.length),
        )

    @Test
    fun `with nothing selected the placeholder is inserted and the caret sits on it`() {
        val state = field("")

        state.edit { applyMarkdown(bold) }

        assertEquals("**加粗文字**", state.text.toString())
        // Just past the opening delimiter, so typing replaces the placeholder in place.
        assertEquals(TextRange(2), state.selection)
    }

    @Test
    fun `a selection is wrapped and the caret goes to the end`() {
        val state = field("出杭州轻量", selection = 0..5)

        state.edit { applyMarkdown(bold) }

        assertEquals("**出杭州轻量**", state.text.toString())
        assertEquals(TextRange("**出杭州轻量**".length), state.selection)
    }

    @Test
    fun `a prefix-only action needs no suffix`() {
        val state = field("关于我", selection = 0..3)

        state.edit { applyMarkdown(heading) }

        assertEquals("## 关于我", state.text.toString())
        assertEquals(TextRange("## 关于我".length), state.selection)
    }

    /** Link is the exception: with the text written, the URL is the only thing left to type. */
    @Test
    fun `link puts the caret in the url slot rather than at the end`() {
        val state = field("星辰担保", selection = 0..4)

        state.edit { applyMarkdown(link) }

        assertEquals("[星辰担保](https://)", state.text.toString())
        assertEquals(TextRange("[星辰担保](".length), state.selection)
    }

    @Test
    fun `an empty link selection puts the caret on the link text`() {
        val state = field("")

        state.edit { applyMarkdown(link) }

        assertEquals("[链接文字](https://)", state.text.toString())
        assertEquals(TextRange(1), state.selection)
    }

    @Test
    fun `formatting applies in the middle of existing text`() {
        val state = field("交易走星辰担保，勿私", selection = 3..7)

        state.edit { applyMarkdown(bold) }

        assertEquals("交易走**星辰担保**，勿私", state.text.toString())
        assertEquals(TextRange("交易走**星辰担保**".length), state.selection)
    }

    /** The buffer clamps a selection to the text on construction; formatting must survive that. */
    @Test
    fun `an out-of-range selection is clamped rather than crashing`() {
        val state = TextFieldState(initialText = "短", initialSelection = TextRange(0, 99))

        state.edit { applyMarkdown(bold) }

        assertEquals("**短**", state.text.toString())
    }
}
