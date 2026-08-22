package io.github.plaza.designsys.richtext

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.LinkPrefetcher
import io.github.plaza.designsys.component.LocalLinkPrefetcher
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The browser is told about a link on the press, not on the release.
 *
 * The whole point of the prefetch is the gap between the two: a browser handed a URL at the moment
 * of the tap starts its DNS lookup then, while one told a hundred milliseconds earlier has the
 * connection open by the time the tab is asked for. A test that only pressed *and released* would
 * pass just as well against a prefetch wired to the click — which would be worth nothing — so every
 * case here stops at `down`.
 *
 * The last two cases are the ones that guard the reader rather than the clock. A press has to warm
 * the link it landed on and nothing else: prefetching is a request to a stranger's host, and a
 * paragraph that merely *contains* a link is not the reader having chosen it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class LinkPressPrefetchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val prefetched = mutableListOf<String>()

    private fun setContent(inlines: List<InlineNode>) {
        composeRule.setContent {
            PlazaTheme {
                CompositionLocalProvider(
                    LocalLinkPrefetcher provides LinkPrefetcher { prefetched += it },
                ) {
                    RichContent(
                        nodes = listOf(RichNode.Paragraph(inlines)),
                        onLinkClick = {},
                        onImageClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun `pressing a link hands its url to the prefetcher before the finger lifts`() {
        setContent(listOf(InlineNode.Link(text = LINK_TEXT, url = NQ_URL)))

        composeRule.onNodeWithText(LINK_TEXT).performTouchInput { down(center) }
        composeRule.waitForIdle()

        assertEquals(listOf(NQ_URL), prefetched)
    }

    @Test
    fun `pressing a paragraph that holds no link warms nothing`() {
        setContent(listOf(InlineNode.Text(PROSE_TEXT)))

        composeRule.onNodeWithText(PROSE_TEXT).performTouchInput { down(center) }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), prefetched)
    }

    @Test
    fun `pressing the prose half of a paragraph does not warm the link in its other half`() {
        setContent(
            listOf(
                InlineNode.Text(PROSE_TEXT),
                InlineNode.Link(text = LINK_TEXT, url = NQ_URL),
            ),
        )

        // The far left of the only line, which is the opening character of the prose — the link
        // sits after all of it. Measured, not assumed: pressing past 60% of this line does warm the
        // link, which is the case below.
        composeRule.onNodeWithText(MIXED_TEXT, substring = true).performTouchInput {
            down(Offset(1f, centerY))
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), prefetched)
    }

    @Test
    fun `pressing the link half of the same paragraph does warm it`() {
        setContent(
            listOf(
                InlineNode.Text(PROSE_TEXT),
                InlineNode.Link(text = LINK_TEXT, url = NQ_URL),
            ),
        )

        composeRule.onNodeWithText(MIXED_TEXT, substring = true).performTouchInput {
            down(Offset(width * 0.8f, centerY))
        }
        composeRule.waitForIdle()

        assertEquals(listOf(NQ_URL), prefetched)
    }

    @Test
    fun `a press that turns into a scroll still warms only the link it started on`() {
        setContent(listOf(InlineNode.Link(text = LINK_TEXT, url = NQ_URL)))

        composeRule.onNodeWithText(LINK_TEXT).performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(NQ_URL), prefetched)
    }
}

private const val LINK_TEXT = "点击查看 NQ"
private const val PROSE_TEXT = "这一段里没有任何链接"
private const val MIXED_TEXT = PROSE_TEXT + LINK_TEXT
private const val NQ_URL = "https://nodequality.com/r/abc"
