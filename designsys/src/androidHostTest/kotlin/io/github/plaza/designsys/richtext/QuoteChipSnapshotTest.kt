package io.github.plaza.designsys.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.CommentBody
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 引用 chip against the two things that used to break it, pinned to a golden.
 *
 * Both failures are in the *drawing* rather than in any value a test could read back, which is why
 * this is a picture: a chip taller than its own line was painted over by the 表情 on the next line,
 * and a chip the line breaker split was drawn as two pills, the second one with its round cap
 * cutting into the glyph that opened the line. A reply's 15/25 body is what makes the first one
 * visible, and a name ending in an emoji is what made the second one obvious.
 *
 * Re-record with `./gradlew :designsys:testAndroidHostTest -ProborazziRecord --rerun-tasks`, the
 * same flow [io.github.plaza.designsys.DesignSystemSnapshotTest] documents.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h360dp")
class QuoteChipSnapshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The cache is one map per process, so a size recorded here must not outlive this test. */
    @After
    fun clearStickerSizes() = StickerSizeCache.clear()

    @Test
    fun `the quote chip keeps clear of the sticker under it and never splits in two`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) { Sample() }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/androidHostTest/snapshots/quote-chip.png",
            roborazziOptions = OPTIONS,
        )
    }

    @Composable
    private fun Sample() {
        StickerSizeCache.record(STICKER_URL, 200, 200)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            CompositionLocalProvider(LocalStickerSizing provides StickerSizing(uniform = false)) {
                RichContent(
                    nodes = SAMPLE_CONTENT,
                    onLinkClick = {},
                    onImageClick = {},
                    // The reply body, not the post body: 15/25 is the tighter line, and the chip
                    // hanging out of it is the whole point of the picture.
                    textStyle = CommentBody,
                )
            }
        }
    }

    private companion object {
        const val STICKER_URL = "https://example.invalid/quote-chip-sticker.png"

        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(resultValidator = ThresholdValidator(0.005f)),
            )

        val SAMPLE_CONTENT: List<RichNode> =
            listOf(
                RichNode.Paragraph(
                    listOf(
                        InlineNode.QuoteRef(name = "starsakura", floor = "#2", url = "/post-1-1#2"),
                        InlineNode.LineBreak,
                        InlineNode.Sticker(url = STICKER_URL, alt = "表情"),
                    ),
                ),
                RichNode.Paragraph(
                    listOf(
                        InlineNode.Text("一行字把引用挤到行尾，再多写几个字挤一挤 "),
                        InlineNode.QuoteRef(name = "🐔腿王🍗", floor = "#128", url = "/post-1-1#128"),
                    ),
                ),
            )
    }
}
