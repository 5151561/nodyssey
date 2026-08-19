package io.github.plaza.designsys.richtext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
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
import coil3.test.FakeImageLoaderEngine
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a reader sees the *second* time an image scrolls onto the screen.
 *
 * A thread is a lazy list, so a row that leaves the screen is disposed and everything the image
 * composable remembered about it goes with it. The image then reloaded from scratch behind a
 * fixed-height spinner, and the row grew back to full height when it finished — a page of
 * Check.Place reports jumped under the finger on every scroll up. The measured size now outlives the
 * row, so the space is held from the first frame.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BlockImageRevisitTest {
    @get:Rule
    val composeRule = createComposeRule()

    // See BlockImageLayoutTest: the singleton outlives the test that set it, and so does the size
    // cache under test here.
    @Before
    fun resetSharedState() {
        SingletonImageLoader.reset()
        resetNaturalImageSizes()
    }

    private val url = "https://img.example.invalid/report.png"

    /** Held open from the second request on, so the revisit is asserted while it is still loading. */
    private val neverArrives = CompletableDeferred<Unit>()

    @Test
    fun `an image that has been seen once holds its own height while it reloads`() {
        var requests = 0
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ it == url }) { chain ->
                    if (requests++ > 0) neverArrives.await()
                    SuccessResult(
                        image = ColorImage(width = 800, height = 600),
                        request = chain.request,
                        dataSource = DataSource.MEMORY,
                    )
                }.build()
        var onScreen by mutableStateOf(true)
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(engine) }.build()
            }
            PlazaTheme {
                if (onScreen) {
                    RichContent(
                        nodes = listOf(RichNode.BlockImage(url = url, alt = "报告")),
                        onLinkClick = {},
                        onImageClick = {},
                    )
                }
            }
        }

        // 360dp of column at 800×600 is 270dp tall.
        composeRule.onNodeWithContentDescription("报告").assertHeightIsEqualTo(270.dp)

        onScreen = false
        composeRule.waitForIdle()
        onScreen = true
        composeRule.waitForIdle()

        // Still loading — the second request is parked — and already the right height rather than
        // the 132dp spinner band.
        composeRule.onNodeWithContentDescription("报告").assertHeightIsEqualTo(270.dp)
    }
}
