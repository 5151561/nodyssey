package io.github.plaza.designsys.richtext

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.UnknownHostException

/**
 * What is left in the post when an image does not arrive.
 *
 * 图片加载失败 alone is what post-879848 ran into: the reader cannot tell a host that refuses the
 * app from a dead link, and 重试 — the only action there was — is useless against the first and the
 * only hope against the second. So the reason has to be on screen, and 用浏览器打开 has to be
 * reachable from here.
 *
 * The classification itself is `ImageLoadFailureTest`'s; this covers that it is shown and that the
 * browser action carries the image's own URL.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BlockImageFailureTest {
    @get:Rule
    val composeRule = createComposeRule()

    // See BlockImageLayoutTest: the singleton outlives the test that set it.
    @Before
    fun resetImageLoader() = SingletonImageLoader.reset()

    private val url = "https://img.example.invalid/file/screenshot.webp"
    private val links = mutableListOf<String>()

    private fun setContent() {
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept(
                    predicate = { it == url },
                    interceptor = { chain ->
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = UnknownHostException("img.example.invalid"),
                        )
                    },
                ).build()
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(engine) }.build()
            }
            PlazaTheme {
                RichContent(
                    nodes = listOf(RichNode.BlockImage(url = url, alt = "截图")),
                    onLinkClick = { links += it },
                    onImageClick = {},
                )
            }
        }
    }

    @Test
    fun `a failed image says why it failed`() {
        setContent()

        composeRule.onNodeWithText("图片加载失败").assertExists()
        composeRule.onNodeWithText("连不上图床，检查一下网络").assertExists()
    }

    @Test
    fun `the browser action opens the image's own url`() {
        setContent()

        composeRule.onNodeWithText("用浏览器打开").performClick()

        assertEquals(listOf(url), links)
    }

    /** 重试 stays: it is the right action for the failures that are worth another request. */
    @Test
    fun `retry is still offered`() {
        setContent()

        composeRule.onNodeWithText("重试").assertExists()
    }
}
