package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.core.html.AnsiParser
import io.github.nodyssey.core.report.QualityReportParser
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.model.RichNode
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Assert.assertEquals
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
 * benchmark report becomes a card and anything else stays a code block — and that the way back to
 * the original is actually reachable.
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
        language: String? = "ansi",
        format: ReportFormat = ReportFormat.ADAPTED,
    ) {
        val decoded = AnsiParser.decode(code)
        compose.setContent {
            NodysseyTheme {
                CompositionLocalProvider(LocalReportFormat provides format) {
                    // The card is taller than a phone, which is the whole problem it exists for.
                    // Without a scroller the assertions below the fold would fail on layout rather
                    // than content.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        RichContent(
                            nodes = listOf(
                                RichNode.CodeBlock(
                                    code = decoded.text,
                                    language = language,
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

    @Test
    fun `capability markers become separate chips`() {
        showCodeBlock(report())

        compose.onNodeWithText("AES-NI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("VT-x/AMD-V").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the original stays one tap away`() {
        showCodeBlock(report())

        compose.onNodeWithText("查看原始报告").performScrollTo().performClick()

        // The command that produced the report is in the banner and nowhere in the card, so finding
        // it means the untouched text is on screen.
        compose.onNodeWithText("bash <(curl -sL https://Check.Place) -H", substring = true).assertExists()
    }

    @Test
    fun `the card collapses from its header`() {
        showCodeBlock(report())

        compose.onNodeWithText("CPU测评").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("硬件质量体检报告").performClick()

        compose.onAllNodes(hasText("CPU测评")).assertCountEquals(0)
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

    @Test
    fun `the source format still opens full screen`() {
        showCodeBlock(report(), format = ReportFormat.SOURCE)

        compose.onNodeWithText("全屏查看").performScrollTo().performClick()

        // 关闭 belongs to the dialog alone; the inline block only offers 复制.
        compose.onNodeWithContentDescription("关闭").assertExists()
    }

    /** `language-ansi` is also how an ordinary coloured paste arrives, and that is not a report. */
    @Test
    fun `anything that is not a report stays a code block`() {
        showCodeBlock("curl -sL https://run.nodequality.com | bash", language = "bash")

        compose.onNodeWithText("curl -sL https://run.nodequality.com | bash").assertIsDisplayed()
        assertEquals(null, QualityReportParser.parse("curl -sL https://run.nodequality.com | bash"))
    }
}
