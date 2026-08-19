package io.github.plaza.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Composes the gallery on the desktop JVM and reads what came out.
 *
 * The window in `Gallery.kt` is what a person opens; this is what CI runs, and it is the difference
 * between "the module compiles for a second target" and "the module draws on a second target". Both
 * `:designsys` and `:shared` are in the tree below, so a failure here is one of them and not this
 * file.
 *
 * Text rather than test tags: none of the components carries one, and adding tags to a shipped
 * module so an unshipped probe can find things would be the probe deciding the library's API.
 */
class GalleryTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the design system composes on the desktop JVM`() = runComposeUiTest {
        setContent { GalleryContent() }

        // `:designsys`'s own components, one per shape: a tag, a measured list row, a settings row.
        onNodeWithText("日常").assertIsDisplayed()
        onNodeWithText("这台小鸡跑 Kotlin/Native 编译要多久").assertIsDisplayed()
        onNodeWithText("仅 Wi-Fi 加载图片").assertIsDisplayed()
    }

    /**
     * The parser and the renderer in the same tree.
     *
     * `迁移记录` is a `RichNode.Heading` that `parseMarkdown` in `:shared` produced and `RichContent`
     * in `:designsys` drew — neither module having been compiled for this platform before.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a body parsed by shared is drawn by designsys`() = runComposeUiTest {
        setContent { GalleryContent() }

        onNodeWithText("迁移记录").assertIsDisplayed()
    }

    /**
     * ANSI decoding survives too, and it is the one that could not be taken for granted: the decoder
     * is `:shared`'s and the colours are `:designsys`'s, but what turns the two into glyphs is the
     * platform's text stack, which is Skia here and was Android's before.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `terminal output keeps its text after the escapes are stripped`() = runComposeUiTest {
        setContent { GalleryContent() }

        // Scrolled to rather than asserted where it stands: the terminal section sits below the
        // viewport in a window this size, and a node that exists off-screen would be a weaker claim
        // than one that was scrolled into view and drawn.
        onNodeWithText("基础信息", substring = true).performScrollTo().assertIsDisplayed()
    }
}
