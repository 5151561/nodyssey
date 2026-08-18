package io.github.plaza.designsys.richtext

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.test.FakeImageLoaderEngine
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 表情统一缩限, as it lands in a line of post text.
 *
 * [StickerSizingTest] covers what the setting computes; this covers whether the number reaches the
 * placeholder — the part that is invisible to a test whose stickers never decode, since natural-size
 * mode has nothing to go on until they do. Hence the fake engine with real dimensions.
 */
@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class InlineStickerSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    /*
     * Both of these outlive a single test. The image loader singleton is the usual one; the size
     * cache is deliberately process-wide, which is exactly what would let the first test in the
     * class hand its measurements to the rest of them.
     */
    @Before
    fun reset() {
        SingletonImageLoader.reset()
        StickerSizeCache.clear()
    }

    private fun setContent(sizing: StickerSizing) {
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept(STICKER_URL, ColorImage(width = 64, height = 48))
                .build()
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(engine) }.build()
            }
            PlazaTheme {
                CompositionLocalProvider(LocalStickerSizing provides sizing) {
                    RichContent(nodes = NODES, onLinkClick = {}, onImageClick = {})
                }
            }
        }
    }

    /** The box every build before the setting existed drew, and still the default. */
    @Test
    fun `uniform sizing keeps a sticker in the line-sized box`() {
        setContent(StickerSizing())

        composeRule.onNodeWithContentDescription(ALT).assertWidthIsEqualTo(20.dp)
        composeRule.onNodeWithContentDescription(ALT).assertHeightIsEqualTo(20.dp)
    }

    /** Square whatever the pixels say — the 64x48 sticker is fitted into the box, not obeyed. */
    @Test
    fun `uniform sizing draws the chosen square`() {
        setContent(StickerSizing(uniform = true, uniformSize = 48.sp))

        composeRule.onNodeWithContentDescription(ALT).assertWidthIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription(ALT).assertHeightIsEqualTo(48.dp)
    }

    /**
     * The reflow this mode pays for: the sticker starts in the fallback box because nothing has
     * decoded it, and the paragraph is laid out again around its real size once it has.
     */
    @Test
    fun `natural sizing grows the box to the sticker once it decodes`() {
        setContent(StickerSizing(uniform = false))

        composeRule.onNodeWithContentDescription(ALT).assertWidthIsEqualTo(64.dp)
        composeRule.onNodeWithContentDescription(ALT).assertHeightIsEqualTo(48.dp)
    }
}

private const val STICKER_URL = "https://example.invalid/static/image/sticker/ac/01.png"
private const val ALT = "笑"

private val NODES =
    listOf(
        RichNode.Paragraph(
            listOf(
                InlineNode.Text("笑死"),
                InlineNode.Sticker(url = STICKER_URL, alt = ALT),
            ),
        ),
    )
