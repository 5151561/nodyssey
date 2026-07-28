package io.github.nodyssey.ui.richtext

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import io.github.nodyssey.core.image.AllowMeteredImage
import io.github.nodyssey.core.image.ImagesDeferredException
import io.github.nodyssey.model.RichNode
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Sizes
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendering tests for post bodies.
 *
 * The parser has its own tests; these cover what only exists once the nodes are on screen — above
 * all the controls the body draws itself, which get none of the padding a Material component would
 * have applied for them.
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
    fun resetImageLoader() = SingletonImageLoader.reset()

    private fun setContent(
        nodes: List<RichNode>,
        imageLoader: ImageLoader? = null,
    ) {
        imageLoader?.let(SingletonImageLoader::setUnsafe)
        composeRule.setContent {
            NodysseyTheme {
                RichContent(nodes = nodes, onLinkClick = {}, onImageClick = {})
            }
        }
    }

    /**
     * A hand-rolled `Row` around a 15dp glyph came to 23dp, which is half of Material's minimum and
     * of the number `Sizes.minTouchTarget` calls the brief's hard requirement. Nothing enforces that
     * for a control the app lays out itself, so this test does.
     */
    @Test
    fun `the code block copy control clears the minimum touch target`() {
        setContent(listOf(RichNode.CodeBlock(code = "val x = 1", language = "kotlin")))

        composeRule
            .onNodeWithText("复制")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Sizes.minTouchTarget)
    }

    @Test
    fun `a code block shows its language`() {
        setContent(listOf(RichNode.CodeBlock(code = "val x = 1", language = "kotlin")))

        composeRule.onNodeWithText("kotlin").assertIsDisplayed()
    }

    /** Regression guard for the assertion above: 48dp is the number, not "whatever it renders". */
    @Test
    fun `the minimum touch target is the Material minimum`() {
        assert(Sizes.minTouchTarget == 48.dp) { "expected 48dp, was ${Sizes.minTouchTarget}" }
    }

    /**
     * 仅 Wi-Fi 加载图片 used to leave a hole in the post: no picture, no explanation, nothing to tap.
     * The reader could not tell a skipped screenshot from a post that never had one.
     */
    @Test
    fun `a skipped image leaves a placeholder that says why`() {
        setContent(
            nodes = listOf(RichNode.BlockImage(url = IMAGE_URL, alt = null)),
            imageLoader = imageLoader(DeferringInterceptor()),
        )

        composeRule.onNodeWithText("点按加载这张图").assertIsDisplayed()
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

    @Test
    fun `tapping the placeholder keeps a visible loading surface until the image arrives`() {
        val allowedRequest = CompletableDeferred<Unit>()
        val interceptor = DeferringInterceptor(allowedRequest)
        setContent(
            nodes = listOf(RichNode.BlockImage(url = IMAGE_URL, alt = null)),
            imageLoader = imageLoader(interceptor),
        )

        composeRule.onNodeWithText("点按加载这张图").performClick()

        composeRule.onNodeWithText("图片加载中…").assertIsDisplayed()
        allowedRequest.complete(Unit)
    }

    private fun imageLoader(interceptor: Interceptor): ImageLoader =
        ImageLoader
            .Builder(ApplicationProvider.getApplicationContext<Context>())
            .components { add(interceptor) }
            .build()

    /**
     * Stands in for [io.github.nodyssey.core.image.ImageNetworkPolicyInterceptor] on a metered
     * network: everything is skipped until a request says the user asked for it by hand.
     */
    private class DeferringInterceptor(
        private val allowedRequest: CompletableDeferred<Unit>? = null,
    ) : Interceptor {
        var sawAllowedRequest = false
            private set

        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
            if (chain.request.getExtra(AllowMeteredImage)) {
                sawAllowedRequest = true
                allowedRequest?.await()
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
