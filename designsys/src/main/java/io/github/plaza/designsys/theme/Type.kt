package io.github.plaza.designsys.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The type scale from the design doc.
 *
 * Two rules shape it, both from the Chinese-reading brief: no italics and no all-caps anywhere
 * (neither renders acceptably in Chinese), and hierarchy comes from weight contrast rather than
 * from size alone — a dense list has no room to separate levels by size.
 *
 * Sizes are in `sp` throughout so the whole app follows the system font scale.
 */
val PlazaTypography =
    Typography(
        displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
        // The wordmark in the home app bar. Heavier and tighter than stock M3 on purpose: it is the
        // one place the app gets to look like itself rather than like a Material template.
        titleLarge =
        TextStyle(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
        ),
        // List row titles. 15/21 rather than M3's 16/24 is what buys the ninth row on a 360×800 screen.
        titleMedium =
        TextStyle(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall =
        TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, letterSpacing = 0.2.sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 25.sp, letterSpacing = 0.2.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    )

/** Applies the in-app reading-size preference on top of Android's system font scale. */
fun plazaTypography(fontScale: Float): Typography {
    val scale = fontScale.coerceIn(0.85f, 1.5f)
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return PlazaTypography.copy(
        bodyLarge = PlazaTypography.bodyLarge.scaled(),
        bodyMedium = PlazaTypography.bodyMedium.scaled(),
        bodySmall = PlazaTypography.bodySmall.scaled(),
    )
}

/**
 * Long-form reading style: 16sp on a 27sp line (1.69) with a hair of tracking.
 *
 * Deliberately looser than any Material role. Chinese has no word spaces, so the line is a solid
 * block of glyphs and needs more leading than Latin text at the same size to stay scannable.
 */
val PostBody = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, letterSpacing = 0.2.sp)

/** Replies are shorter and more numerous, so they sit one step tighter than [PostBody]. */
val CommentBody = TextStyle(fontSize = 15.sp, lineHeight = 25.sp, letterSpacing = 0.2.sp)

/**
 * Upgrades a body style for long-form reading. Applied by `RichContent`, never by an editor.
 *
 * [LineBreak.Simple] is the greedy breaker: fill each line as far as it goes, then move on. It is
 * what every browser does, and matching it is the point — the site is read on both.
 *
 * This used to be [LineBreak.Paragraph], the platform's optimal breaker. Optimal breaking spends
 * its budget minimising raggedness across a whole paragraph, which pays off in justified Latin
 * setting and costs in Chinese: a reader of a solid block of hanzi expects a flush right edge and
 * reads evenly distributed slack as every line stopping short. Measured on post-584268's heading
 * at the 1.2 system font scale, greedy fills the opening line to 0.94 of the column where optimal
 * left it at 0.76. The trade is real and known — a paragraph carrying a long unbreakable token,
 * a URL most often, keeps that token whole and so leaves a wider gap on the line above it than
 * optimal would; see `ProseLineBreakTest`, which pins both halves.
 *
 * Strictness stays at [LineBreak.Simple]'s Normal rather than Strict for the same reason: Normal
 * is CSS `line-break: auto`, which is what the site's stylesheet resolves to. Baseline UAX#14
 * already keeps `。，）` off the head of a line; Strict only adds rules Japanese needs.
 *
 * The centred, untrimmed [LineHeightStyle] replaces the default Proportional + Trim.Both, under
 * which a paragraph's first and last half-leading were cut off and the gap between two paragraphs
 * measured barely more than the gap between two lines. Keeping the half-leading makes the space
 * between blocks read as block spacing *plus* line rhythm, which is what gives long posts their
 * paragraph structure back.
 *
 * This must stay off text *fields*, whose [LineHeightStyle] an editor should not inherit.
 */
fun TextStyle.asProse(): TextStyle =
    copy(
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.Auto,
        lineHeightStyle =
        LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

/**
 * A floor's signature, one step under the body it hangs off.
 *
 * Derived from that body's own style rather than fixed, so the step holds at every reading size:
 * the signature has to stay the quieter voice whether the body is set to 13sp or 24sp, and a fixed
 * 13sp label overtook the body as soon as the reading size went below default.
 *
 * The weight drops back to normal because `labelMedium`'s 500 was making a 13sp signature read
 * heavier — and so bigger — than the 15sp reply above it. The site does the same thing more
 * bluntly: it runs signatures at 13px against a 14px body and resets `strong` inside them to 400.
 */
fun TextStyle.asSignature(): TextStyle =
    copy(
        fontSize = fontSize * SIGNATURE_SCALE,
        lineHeight = lineHeight * SIGNATURE_SCALE,
        fontWeight = FontWeight.Normal,
    )

private const val SIGNATURE_SCALE = 0.85f

/**
 * The full title on the detail screen, where it may wrap to several lines.
 *
 * Greedy, for [asProse]'s reason and more sharply. This was [LineBreak.Heading], whose Balanced
 * strategy equalises the wrapped lines; on a two-line Chinese title that means halving it, and
 * `NodeSeek 签到脚本更新，支持自动随机延迟` came out filling 0.60 of the first line with the rest
 * of the column empty beside it. Greedy puts the same title at 0.97. A balanced heading is a
 * typographic nicety for a Latin display face and reads as a layout fault in hanzi, which have no
 * word spaces for the eye to forgive.
 */
val PostTitle =
    TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        lineBreak = LineBreak.Simple,
    )

/**
 * Benchmark report data, one step denser than any reading style.
 *
 * A report card is scanned for a single fact, not read line by line, so it trades the reading
 * styles' generous leading for the density of the terminal output it replaces. Fixed sizes on
 * purpose: the card is a data surface like [CodeStyle] and does not follow the reading-size
 * preference, which keeps an 80-column report's worth of fields on one screen at any setting.
 */
val ReportData = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp)

/** The label column beside [ReportData] values; same 19sp pitch so wrapped rows stay in step. */
val ReportLabel = TextStyle(fontSize = 12.sp, lineHeight = 19.sp)

val CodeStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )

/**
 * Tabular figures for anything numeric that sits in a fixed row.
 *
 * Reply and view counts span `0` to `30594`, and floor numbers `#0` to `#127`. With proportional
 * digits the meta line visibly shifts as rows recycle during a fling.
 */
const val TABULAR_FIGURES = "tnum"
