package io.github.nodyssey.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.plaza.core.ansi.AnsiDecoder
import io.github.plaza.designsys.component.rememberTerminalText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Whether a coloured run is actually legible once both of its colours are chosen.
 *
 * The palettes are tested through the escape sequences `hardware.sh` really emits rather than through
 * their indices: the bug they encode is not a wrong colour, it is a pair of separately reasonable
 * lookups that collide, and only a pair can show that.
 */
@RunWith(RobolectricTestRunner::class)
class AnsiTextTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The style each source ends up drawn with.
     *
     * All of them in one `setContent` because a rule only affords one, and one run each because a
     * source here is a single escaped run by construction.
     */
    private fun styleOf(vararg sources: String): List<SpanStyle> {
        val decoded = sources.map { AnsiDecoder.decode(it) }
        val drawn = mutableListOf<AnnotatedString>()
        compose.setContent { decoded.forEach { drawn += rememberTerminalText(it.text, it.spans) } }
        compose.waitForIdle()
        return drawn.map { it.spanStyles.single().item }
    }

    /** `hardware.sh:3097` — `$Back_White$Font_Black`, the bar across the CPU, memory and disk lines. */
    @Test
    fun `the inverse video bar is dark ink on a light fill`() {
        val style = styleOf("\u001B[47m\u001B[30m Intel Xeon Gold 6138 \u001B[0m").single()

        assertTrue("the fill went dark, so the bar reads as a smudge", style.background.luminance() > 0.5f)
        assertTrue("the ink stayed light, so it vanishes into the fill", style.color.luminance() < 0.2f)
        assertTrue(
            "black on white came out at ${contrastOf(style.color, style.background)}:1",
            contrastOf(style.color, style.background) >= 4.5f,
        )
    }

    /** Unset ink on a light fill is the terminal's default, which is light and would disappear. */
    @Test
    fun `a light fill with no ink set still gets dark ink`() {
        val style = styleOf("\u001B[47m   padding   \u001B[0m").single()

        assertTrue(contrastOf(style.color, style.background) >= 4.5f)
    }

    private fun contrastOf(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
