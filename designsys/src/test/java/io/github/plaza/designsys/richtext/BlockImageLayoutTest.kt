package io.github.plaza.designsys.richtext

import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.decode.DataSource
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.test.FakeImageLoaderEngine
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Image size decides image layout, so these tests feed the loader real (fake) pixels.
 *
 * The two behaviours under test are the web parity fixes for small images: an image narrower than
 * the column keeps its natural size instead of being stretched into a banner, and a run of small
 * images shares a row instead of stacking one banner per line. Both are invisible to a test whose
 * images never load — an unloaded image falls back to full width by design — which is what the
 * fake engine is for.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BlockImageLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The singleton loader survives across tests (and test classes) in one JVM, and
    // `setSingletonImageLoaderFactory` only takes effect on an unset singleton — without the reset,
    // whichever test ran first would supply every later test's images.
    @Before
    fun resetImageLoader() = SingletonImageLoader.reset()

    private val badge1 = "https://example.invalid/runs.svg"
    private val badge2 = "https://example.invalid/license.svg"
    private val screenshot = "https://example.invalid/result.png"

    private fun setContent(nodes: List<RichNode>) {
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept(badge1, ColorImage(width = 132, height = 20))
                .intercept(badge2, ColorImage(width = 108, height = 20))
                .intercept(screenshot, ColorImage(width = 800, height = 600))
                .build()
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(engine) }.build()
            }
            PlazaTheme {
                RichContent(nodes = nodes, onLinkClick = {}, onImageClick = {})
            }
        }
    }

    /** 132 源像素按网页端的规矩读作 132dp，不再被拉满 360dp 的列宽。 */
    @Test
    fun `an image narrower than the column keeps its natural size`() {
        setContent(listOf(RichNode.BlockImage(url = badge1, alt = "runs")))

        composeRule.onNodeWithContentDescription("runs").assertWidthIsEqualTo(132.dp)
    }

    @Test
    fun `an image wider than the column still fills it`() {
        setContent(listOf(RichNode.BlockImage(url = screenshot, alt = "截图")))

        composeRule.onNodeWithContentDescription("截图").assertWidthIsEqualTo(360.dp)
    }

    /**
     * SVG 是按请求尺寸光栅化的：不问先答的话，「这张图多大」的答案永远是「和容器一样大」。
     * 这个假引擎复刻该行为——请求多大就返回多大，未指定尺寸时才交出声明尺寸 132×20 —— 徽章
     * 必须仍量出 132dp，而不是被光栅化的容器宽度。
     */
    @Test
    fun `an svg badge keeps its declared size despite request-sized rasterisation`() {
        val svg = "https://example.invalid/hits.svg?action=view"
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ it == svg }) { chain ->
                    val width = (chain.size.width as? Dimension.Pixels)?.px ?: 132
                    val height = (chain.size.height as? Dimension.Pixels)?.px ?: 20
                    SuccessResult(
                        image = ColorImage(width = width, height = height),
                        request = chain.request,
                        dataSource = DataSource.MEMORY,
                    )
                }.build()
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(engine) }.build()
            }
            PlazaTheme {
                RichContent(
                    nodes = listOf(RichNode.BlockImage(url = svg, alt = "hits")),
                    onLinkClick = {},
                    onImageClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("hits").assertWidthIsEqualTo(132.dp)
    }

    /** 相邻的两个徽章同行并排；跟在后面的整宽截图另起一行 — 网页端行内流的样子。 */
    @Test
    fun `adjacent small images share a row and a wide one takes its own`() {
        setContent(
            listOf(
                RichNode.BlockImage(url = badge1, alt = "runs"),
                RichNode.BlockImage(url = badge2, alt = "license"),
                RichNode.BlockImage(url = screenshot, alt = "截图"),
            ),
        )

        val first = composeRule.onNodeWithContentDescription("runs").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithContentDescription("license").getUnclippedBoundsInRoot()
        val third = composeRule.onNodeWithContentDescription("截图").getUnclippedBoundsInRoot()
        assertEquals("badges should sit on one row", first.top, second.top)
        assertTrue("the two badges must not overlap", second.left >= first.right)
        assertTrue("the screenshot starts below the badges", third.top >= first.bottom)
    }
}
