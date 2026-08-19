package io.github.plaza.designsys.richtext

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.test.FakeImageLoaderEngine
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A post body's table goes to the layout its content can survive.
 *
 * The routing rule under test: a grid whose every cell fits its single-line column goes to the
 * pinned-and-scrolling `SpecTable`, and anything else — a prose cell past the column cap, any cell
 * holding an image — goes to `WrapTable`, where cells wrap instead of being ellipsized. The wrong
 * routing is not cosmetic: `SpecTable` cuts what it cannot fit, so a prose table sent there loses
 * content with no way to reach it, which is the bug this split exists to close.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class DataTableRoutingTest {
    @get:Rule
    val composeRule = createComposeRule()

    // See BlockImageLayoutTest: the singleton loader outlives a test, so each test resets it and
    // supplies its own — every URL resolves to a small solid image, enough for a thumbnail to
    // exist and be measured.
    @Before
    fun resetImageLoader() {
        SingletonImageLoader.reset()
        // The measured-size cache is process-wide too — same hazard, same place to clear it.
        resetNaturalImageSizes()
    }

    private fun cell(text: String): List<InlineNode> = listOf(InlineNode.Text(text))

    private fun setContent(table: RichNode.Table) {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(width = 160, height = 200)).build()
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(engine) }.build()
            }
            PlazaTheme {
                RichContent(nodes = listOf(table), onLinkClick = {}, onImageClick = {})
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = composeRule.onNodeWithText(text).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    /** 长文字单元格：整格换行显示，不截断 — 走的是 WrapTable。 */
    @Test
    fun `a prose cell past the column cap wraps instead of being ellipsized`() {
        val prose = "这一段套餐说明写得很长很长，包含线路、带宽、流量、售后条款和一串补充备注，远超单列的上限宽度。"
        setContent(
            RichNode.Table(
                cells = listOf(
                    listOf(cell("项目"), cell("说明")),
                    listOf(cell("套餐"), cell(prose)),
                ),
            ),
        )

        val layout = layoutOf(prose)
        assertTrue("prose cell should wrap onto multiple lines", layout.lineCount > 1)
        assertFalse("prose cell must not be cut", layout.hasVisualOverflow)
        // The wrapping layout has no sideways scroll: the whole table is on screen.
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    /** 全是短数值的表：仍走 SpecTable，标志是那条横向滚动通道。 */
    @Test
    fun `a numeric grid keeps the single-line scrolling table`() {
        setContent(
            RichNode.Table(
                cells = listOf(
                    listOf(cell("节点"), cell("延迟"), cell("丢包")),
                    listOf(cell("电信"), cell("56.4"), cell("0%")),
                    listOf(cell("联通"), cell("48.2"), cell("1%")),
                ),
            ),
        )

        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(1)
        val layout = layoutOf("56.4")
        assertTrue(layout.lineCount == 1)
        assertFalse(layout.hasVisualOverflow)
    }

    /**
     * `/post-287967-1` 成绩表的精确结构：thead 两个文字表头 + 一行两张截图。
     * 两张缩略图必须左右并排在同一行，而不是叠成一列。
     */
    @Test
    fun `two image cells in one row sit side by side`() {
        setContent(
            RichNode.Table(
                cells = listOf(
                    listOf(cell("IPv4测试结果"), cell("IPv6测试结果")),
                    listOf(
                        listOf(InlineNode.Image("https://example.invalid/v4.png", "IPv4")),
                        listOf(InlineNode.Image("https://example.invalid/v6.png", "IPv6")),
                    ),
                ),
            ),
        )

        val left = composeRule.onNodeWithContentDescription("IPv4").getUnclippedBoundsInRoot()
        val right = composeRule.onNodeWithContentDescription("IPv6").getUnclippedBoundsInRoot()
        assertTrue("cells share one row", left.top == right.top)
        assertTrue("cells must not overlap", right.left >= left.right)
    }

    /** 单元格里的截图必须画出来 — `/post-287967-1` 的 2×2 成绩表在这之前整个消失。 */
    @Test
    fun `a cell image renders as a thumbnail`() {
        setContent(
            RichNode.Table(
                cells = listOf(
                    listOf(cell("IPv4"), cell("IPv6")),
                    listOf(
                        listOf(InlineNode.Image("https://example.invalid/v4.png", "IPv4测试结果")),
                        listOf(InlineNode.Image("https://example.invalid/v6.png", "IPv6测试结果")),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithContentDescription("IPv4测试结果").assertExists()
        composeRule.onNodeWithContentDescription("IPv6测试结果").assertExists()
    }
}
