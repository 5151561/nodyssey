package io.github.nodyssey.ui.richtext

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.image.AllowMeteredImage
import io.github.plaza.designsys.image.ImagesDeferredException
import io.github.plaza.designsys.richtext.resetNaturalImageSizes
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/**
 * Rendering tests for post bodies.
 *
 * The parser has its own tests; these cover what only exists once the nodes are on screen — where a
 * link actually goes, and what an image that did not load leaves behind.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class RichContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    /*
     * `setUnsafe`, not the composable `setSingletonImageLoaderFactory`: that one delegates to
     * `setSafe`, which is a no-op once anything in the suite has already touched the singleton. The
     * image tests passed alone and failed in the full run until this was forced.
     */
    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
        // The measured-size cache is process-wide too — same hazard, same place to clear it.
        resetNaturalImageSizes()
    }

    private fun setContent(
        nodes: List<RichNode>,
        imageLoader: ImageLoader? = null,
        onLinkClick: (String) -> Unit = {},
    ) {
        imageLoader?.let(SingletonImageLoader::setUnsafe)
        composeRule.setContent {
            PlazaTheme {
                PostRichContent(nodes = nodes, onLinkClick = onLinkClick, onImageClick = {})
            }
        }
    }

    /**
     * A 拼车 post files its NodeQuality reports in a table column, and the cell used to be read as a
     * plain string — the reader could see "点击查看 NQ" and could not follow it.
     */
    @Test
    fun `a link in a table cell stays a link`() {
        val followed = mutableListOf<String>()
        setContent(nodes = listOf(NQ_TABLE), onLinkClick = followed::add)

        val cell = composeRule.onNodeWithText("NQ").assertIsDisplayed().fetchSemanticsNode()
        val text = cell.config[SemanticsProperties.Text].single()
        val url = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Url
        assertTrue("expected the NQ report's URL, was ${url.url}", url.url == NQ_URL)

        // Clicked on the first glyph rather than at the node's middle, which is where `performClick`
        // would land: a narrow table is stretched to fill its container, so the cell is far wider
        // than its two-letter label and the label does not reach halfway.
        composeRule.onNodeWithText("NQ").performTouchInput {
            click(Offset(x = left + CELL_PADDING_PX, y = centerY))
        }

        assertTrue("expected the tap to be reported, got $followed", followed == listOf(NQ_URL))
    }

    /**
     * The preference stops the app spending data on its own; it must not stop the reader who taps.
     * Tapping the placeholder re-requests the same image with the preference waived for it alone.
     */
    @Test
    fun `tapping the placeholder loads the image anyway`() {
        val interceptor = DeferringInterceptor()
        setContent(
            nodes = listOf(RichNode.BlockImage(url = IMAGE_URL, alt = null)),
            imageLoader = imageLoader(interceptor),
        )

        composeRule.onNodeWithText("点按加载这张图").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("点按加载这张图").assertDoesNotExist()
        assertTrue("expected a request that waives the preference", interceptor.sawAllowedRequest)
    }

    /**
     * The other half of the same rule, and the one the deferred tests never covered: an image whose
     * fetch is *tried* and fails must leave the failure on screen, with the retry that fixes it.
     */
    @Test
    fun `a block image that fails leaves a failure notice and a retry`() {
        setContent(
            nodes = listOf(RichNode.BlockImage(url = IMAGE_URL, alt = null)),
            imageLoader = imageLoader(FailingInterceptor()),
        )

        composeRule.onNodeWithText("图片加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    /**
     * A sticker had no failure state at all: `AsyncImage` draws nothing on error, so the 20sp box
     * stayed empty and a reply written entirely in 表情 came out blank — indistinguishable from a
     * post with nothing in it. Under 仅 Wi-Fi 加载图片 that was every sticker in the thread.
     */
    @Test
    fun `a sticker that fails leaves a mark rather than a gap`() {
        setContent(
            nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Sticker(url = IMAGE_URL, alt = ":ac01:")))),
            imageLoader = imageLoader(FailingInterceptor()),
        )

        composeRule.onNodeWithContentDescription("图片加载失败").assertIsDisplayed()
    }

    /** A skipped sticker says it was skipped, not that it broke — the two have different fixes. */
    @Test
    fun `a skipped sticker is marked as skipped rather than as broken`() {
        setContent(
            nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Sticker(url = IMAGE_URL, alt = ":ac01:")))),
            imageLoader = imageLoader(DeferringInterceptor()),
        )

        composeRule
            .onNodeWithContentDescription("已开启「仅 Wi-Fi 加载图片」，当前不是 Wi-Fi")
            .assertIsDisplayed()
    }

    private fun imageLoader(interceptor: Interceptor): ImageLoader =
        ImageLoader
            .Builder(ApplicationProvider.getApplicationContext<Context>())
            .components { add(interceptor) }
            .build()

    /** An ordinary transport failure: the fetch was attempted and did not come back with pixels. */
    private class FailingInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
            ErrorResult(
                image = null,
                request = chain.request,
                throwable = IOException("no route to host"),
            )
    }

    /**
     * Stands in for [io.github.nodyssey.core.image.ImageNetworkPolicyInterceptor] on a metered
     * network: everything is skipped until a request says the user asked for it by hand.
     */
    private class DeferringInterceptor : Interceptor {
        var sawAllowedRequest = false
            private set

        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
            if (chain.request.getExtra(AllowMeteredImage)) {
                sawAllowedRequest = true
                SuccessResult(
                    image = ColorImage(width = 100, height = 100),
                    request = chain.request,
                    dataSource = DataSource.MEMORY,
                )
            } else {
                ErrorResult(
                    image = null,
                    request = chain.request,
                    throwable = ImagesDeferredException(),
                )
            }
    }
}

private const val IMAGE_URL = "https://www.nodeseek.com/static/example.png"
private const val NQ_URL = "https://nodequality.com/r/abc"

/** A cell's horizontal padding, plus a glyph's width, at this test's 1x density. */
private const val CELL_PADDING_PX = 12f

private val NQ_TABLE =
    RichNode.Table(
        cells =
        listOf(
            listOf("车次", "节点范围", "报告").map { listOf(InlineNode.Text(it)) },
            listOf(
                listOf(InlineNode.Text("一号车")),
                listOf(InlineNode.Text("VMISS 美西")),
                listOf(InlineNode.Link(text = "NQ", url = NQ_URL)),
            ),
        ),
    )
