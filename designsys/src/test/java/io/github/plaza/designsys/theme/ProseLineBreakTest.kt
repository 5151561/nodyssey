package io.github.plaza.designsys.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Which line breaker [asProse] hands the body text, pinned from both sides.
 *
 * The forum is read in a browser as well as here, and a browser breaks greedily: fill the line,
 * move on. Optimal breaking spreads a paragraph's slack over every line instead, which in Chinese
 * — where the reader expects a flush right edge from a solid block of hanzi — reads as every line
 * stopping short. Both tests below measure the same string under both breakers, so the win and the
 * cost of the choice stay visible to whoever reads this next.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ProseLineBreakTest {
    private val density = Density(RuntimeEnvironment.getApplication())

    private val measurer =
        TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(RuntimeEnvironment.getApplication()),
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr,
        )

    /** How much of the column each line but the last one fills; the last is short by definition. */
    private fun fillRatios(
        text: String,
        style: TextStyle,
        width: Int = with(density) { 360.dp.roundToPx() },
    ): List<Float> {
        val result =
            measurer.measure(
                text = AnnotatedString(text),
                style = style,
                constraints = Constraints(maxWidth = width),
            )
        return (0 until result.lineCount - 1).map { line ->
            (result.getLineRight(line) - result.getLineLeft(line)) / width
        }
    }

    private fun greedy(base: TextStyle) = base.asProse()

    private fun optimal(base: TextStyle) = base.asProse().copy(lineBreak = LineBreak.Paragraph)

    @Test
    fun `asProse breaks greedily, the way the site does in a browser`() {
        assertEquals(LineBreak.Simple, PostBody.asProse().lineBreak)
    }

    /**
     * The win: a short block ending in a token that cannot break.
     *
     * post-584268 opens a section with this heading, and at the 1.2 system font scale the string
     * is a hair too wide for one line. Optimal breaking answers by shortening *every* line; greedy
     * fills the first and pushes the whole remainder down, which is what the browser shows.
     */
    @Test
    fun `greedy fills the opening line of a heading that ends in a URL`() {
        val heading = "GitHub项目地址（欢迎Star关注）： https://github.com/xykt/HardwareQuality"
        val style = PostBody.copy(fontSize = PostBody.fontSize * 1.35f)

        val greedyFirst = fillRatios(heading, greedy(style)).first()
        val optimalFirst = fillRatios(heading, optimal(style)).first()

        assertTrue(
            "greedy should fill the opening line further than optimal, got $greedyFirst vs $optimalFirst",
            greedyFirst > optimalFirst,
        )
    }

    /**
     * The cost, pinned so that it is a decision rather than a surprise.
     *
     * Greedy keeps a long unbreakable token whole and drops it entire to the next line, so the
     * line above it ends further short than optimal would leave it. This is the trade the choice
     * of greedy accepts; it is not a regression to be quietly reverted. If this ever needs fixing
     * it wants breakable URLs, not a different paragraph breaker.
     */
    @Test
    fun `greedy leaves a wider gap above a long unbreakable URL`() {
        val prose =
            "这个问题的根源在于 systemd-resolved 抢管 resolv.conf，参考 " +
                "https://wiki.archlinux.org/title/Systemd-resolved 里的说明，改完记得重启服务。"

        val worstGreedy = fillRatios(prose, greedy(PostBody)).min()
        val worstOptimal = fillRatios(prose, optimal(PostBody)).min()

        assertTrue(
            "the documented cost of greedy should still be visible, got $worstGreedy vs $worstOptimal",
            worstGreedy < worstOptimal,
        )
    }

    /**
     * The detail screen's title, which had the worst case of the lot.
     *
     * [LineBreak.Heading]'s Balanced strategy halves a two-line title; this one came out at 0.60
     * of the column with the rest empty beside it, which is the complaint that started all this.
     */
    @Test
    fun `a post title fills its first line`() {
        val title = "NodeSeek 签到脚本更新，支持自动随机延迟"

        val balanced = fillRatios(title, PostTitle.copy(lineBreak = LineBreak.Heading)).first()
        val greedy = fillRatios(title, PostTitle).first()

        assertTrue("a title's first line should be near flush, got $greedy", greedy > 0.9f)
        assertTrue("this test is only meaningful while Balanced does worse, got $balanced", balanced < 0.8f)
    }

    /** Pure Chinese is the common case and must stay flush under the greedy breaker. */
    @Test
    fun `pure Chinese fills every line`() {
        val prose =
            "最近折腾了一下家里的软路由，把旁路由的方案换成了主路由直接跑，稳定性比之前好了不少。" +
                "以前每次重启主路由都要手动去改网关，现在完全不用管了，插上电就能自己跑起来。"

        fillRatios(prose, greedy(PostBody)).forEach { ratio ->
            assertTrue("a pure Chinese line should be near flush, got $ratio", ratio > 0.95f)
        }
    }
}
