package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.data.settings.ReportFormat
import io.github.plaza.core.ansi.AnsiDecoder
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the report looks like once it is on screen rather than in a data class.
 *
 * The parser's own tests cover the reading; these cover the decision the renderer makes — that a
 * benchmark report becomes a card, or under 原文 stays the text as posted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ReportCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun report(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/reports/hardware-quality.txt"))
            .bufferedReader()
            .readText()

    private fun showCodeBlock(
        code: String,
        format: ReportFormat = ReportFormat.ADAPTED,
    ) {
        val decoded = AnsiDecoder.decode(code)
        compose.setContent {
            PlazaTheme {
                CompositionLocalProvider(LocalReportFormat provides format) {
                    // The card is taller than a phone, which is the whole problem it exists for.
                    // Without a scroller the assertions below the fold would fail on layout rather
                    // than content.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        PostRichContent(
                            nodes = listOf(
                                RichNode.CodeBlock(
                                    code = decoded.text,
                                    language = "ansi",
                                    spans = decoded.spans,
                                    columns = decoded.columns,
                                ),
                            ),
                            onLinkClick = {},
                            onImageClick = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a benchmark report is drawn as rows, not as terminal art`() {
        showCodeBlock(report())

        // A label and its value, each a node of its own — which is what the padding used to encode.
        compose.onNodeWithText("硬件质量体检报告").assertIsDisplayed()
        compose.onNodeWithText("容器/虚拟化").assertIsDisplayed()
        compose.onNodeWithText("KVM 虚拟机").assertIsDisplayed()

        // Section headings survive without their numbering.
        compose.onNodeWithText("CPU测评").performScrollTo().assertIsDisplayed()
    }

    /** 测评报告 = 原文: the same block, drawn as it was posted rather than read apart. */
    @Test
    fun `the source format draws the report as posted instead of as rows`() {
        showCodeBlock(report(), format = ReportFormat.SOURCE)

        // The banner command is in the original and nowhere in the card, so finding it without
        // tapping anything means the untouched text is what is on screen.
        compose.onNodeWithText("bash <(curl -sL https://Check.Place) -H", substring = true)
            .assertExists()
        // And the card's own rows — a label the parser lifted out of the padding — are not.
        compose.onAllNodes(hasText("容器/虚拟化")).assertCountEquals(0)
    }

    /**
     * 原文 is the output as the script wrote it, and the script wrote its verdicts in ANSI — a
     * monochrome 原文 is a different report rather than a plainer one.
     */
    @Test
    fun `the source format keeps the report's colours`() {
        // The fixtures are saved with their escapes stripped, so the verdict gets its green back here
        // — a report that arrives uncoloured cannot tell whether the colour survived the drawing.
        val coloured = report().replace("KVM 虚拟机", "\u001B[32mKVM 虚拟机\u001B[0m")
        showCodeBlock(coloured, format = ReportFormat.SOURCE)

        val drawn = compose.onNodeWithText(BANNER, substring = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .single()

        assertTrue(
            "原文 was drawn without any of the report's colour runs",
            drawn.spanStyles.any { it.item.color.isSpecified || it.item.background.isSpecified },
        )
    }

    /**
     * Regression: a pinch rests a finger long enough to fire the long press that starts a text
     * selection, and the dialog is a window of its own. Its text must not register with the post's
     * `SelectionContainer`, whose container coordinates belong to the other layout root — asking one
     * to be mapped into the other threw `layouts are not part of the same hierarchy` and took the app
     * down mid-zoom.
     */
    @Test
    fun `a long press inside the full screen original does not cross selection hierarchies`() {
        showCodeBlock(report(), format = ReportFormat.SOURCE)
        compose.onNodeWithText("全屏查看").performScrollTo().performClick()

        // The banner is in the inline block as well as the dialog; the dialog's is the later node.
        compose.onAllNodes(hasText(BANNER, substring = true)).onLast()
            .performTouchInput { longClick() }

        compose.onNodeWithContentDescription("关闭").assertExists()
    }
}

/** In the report's banner and nowhere in the card, so finding it means the original is on screen. */
private const val BANNER = "bash <(curl -sL https://Check.Place) -H"
