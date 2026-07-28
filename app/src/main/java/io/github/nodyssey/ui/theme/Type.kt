package io.github.nodyssey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
val NodysseyTypography =
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
fun nodysseyTypography(fontScale: Float): Typography {
    val scale = fontScale.coerceIn(0.85f, 1.5f)
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return NodysseyTypography.copy(
        bodyLarge = NodysseyTypography.bodyLarge.scaled(),
        bodyMedium = NodysseyTypography.bodyMedium.scaled(),
        bodySmall = NodysseyTypography.bodySmall.scaled(),
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

/** The full title on the detail screen, where it may wrap to several lines. */
val PostTitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)

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
