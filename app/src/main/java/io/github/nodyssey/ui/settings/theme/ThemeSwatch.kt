package io.github.nodyssey.ui.settings.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.plaza.designsys.theme.PlazaDefaultSeed

/**
 * One colour dot, in the two places a seed is picked as a colour: the wallpaper candidates and the
 * swatch beside a saved theme's name. 预设 draws faces instead — see [PresetDot].
 *
 * The selected state is a ring standing off the dot rather than a border on it — 2dp of ground, then
 * 2dp of `primary` — which is what keeps a dark seed from losing its edge against a dark surface.
 * The ground is painted rather than left transparent because the ring's gap has to be
 * `surfaceContainerLow` even where the dot sits on the preset grid's `surfaceContainer` plate.
 *
 * [second] is the dot's other half, split on the 135° diagonal. Preset dots carry one because a
 * scheme is a pair of families and a single circle only ever showed half of what the tap would do.
 *
 * The dot is only the target where it is the whole cell. Where a name sits under it and belongs to
 * the same choice, the target is the cell and [onClick] is left null — a circle with a label beside
 * it that does nothing when tapped is a control people report as broken.
 */
@Composable
internal fun ThemeSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    /** Null where the dot is part of a larger target and the caller has already made that target. */
    onClick: (() -> Unit)? = null,
    second: Color? = null,
    size: Dp = SwatchSize,
    ringGround: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    Box(
        modifier =
        modifier
            .size(size + RingInset * 2)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                },
            ).drawBehind {
                if (!selected) return@drawBehind
                val outer = this.size.minDimension / 2f
                drawCircle(ringColor, radius = outer)
                drawCircle(ringGround, radius = outer - RingWidth.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(swatchBrush(color, second)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = label,
                    tint = contentColorOn(color),
                    modifier = Modifier.size(size * CHECK_RATIO),
                )
            }
        }
    }
}

/**
 * A hard split at 55% along the 135° diagonal, matching j1's `linear-gradient`.
 *
 * Two stops at the same offset rather than a blend: the point is to show two colours, and a gradient
 * between them produces a third that belongs to neither palette.
 */
private fun swatchBrush(color: Color, second: Color?): Brush =
    if (second == null) {
        Brush.linearGradient(listOf(color, color))
    } else {
        Brush.linearGradient(0f to color, SPLIT_AT to color, SPLIT_AT to second, 1f to second)
    }

/** Black or white, whichever the swatch can be read against. */
internal fun contentColorOn(color: Color): Color =
    if (color.luminance() > 0.4f) Color.Black else Color.White

/**
 * A preset's face — 石墨青's two-tone dot, or one of the five flat portraits.
 *
 * Selection is a ring and a badge rather than the check [ThemeSwatch] writes inside the dot: a tick
 * over 初音未来's face would be sitting on the thing the reader is looking at. j2 draws it as 2dp of
 * plate then 2dp of `primary`, with the badge hung off the bottom-right corner.
 *
 * Unselected is 70% desaturated behind a hairline of `outlineVariant`, which is what turns the grid
 * into one selected face among five quiet ones instead of six competing portraits. The avatar gets
 * there through a colour matrix and 石墨青 through the same arithmetic applied to its two colours —
 * a `Brush` has no filter to hang one on.
 */
@Composable
internal fun PresetDot(
    @DrawableRes avatar: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** What the ring's gap is painted with: whatever the dot is sitting on. */
    plate: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val scheme = MaterialTheme.colorScheme
    val ringColor = scheme.primary
    Box(
        modifier =
        modifier
            .size(PresetSwatchSize + RingInset * 2)
            .drawBehind {
                if (!selected) return@drawBehind
                val outer = this.size.minDimension / 2f
                drawCircle(ringColor, radius = outer)
                drawCircle(plate, radius = outer - RingWidth.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        val face =
            Modifier
                .size(PresetSwatchSize)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(HairlineWidth, scheme.outlineVariant, CircleShape)
                    },
                )
        if (avatar == null) {
            val two =
                if (selected) {
                    swatchBrush(PlazaDefaultSeed, GraphiteCompanion)
                } else {
                    swatchBrush(PlazaDefaultSeed.desaturated(), GraphiteCompanion.desaturated())
                }
            Box(face.background(two))
        } else {
            Image(
                painter = painterResource(avatar),
                contentDescription = null,
                modifier = face,
                colorFilter = if (selected) null else DesaturateFilter,
            )
        }
        if (selected) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = -BadgeRise)
                    .size(BadgeSize)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(BadgeCheckSize),
                )
            }
        }
    }
}

/** CSS `grayscale(.7)`, which keeps 30% of the colour and the same luminance weights Compose uses. */
private val DesaturateFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(KEPT_SATURATION) })

private fun Color.desaturated(): Color {
    val gray = LUMA_R * red + LUMA_G * green + LUMA_B * blue
    return copy(
        red = lerp(gray, red, KEPT_SATURATION),
        green = lerp(gray, green, KEPT_SATURATION),
        blue = lerp(gray, blue, KEPT_SATURATION),
    )
}

/** 44dp: the wallpaper candidates, and Material's minimum for something you have to hit. */
internal val SwatchSize = 44.dp

/** 56dp, the preset grid's — it carries a name and a colour summary under it and can afford it. */
internal val PresetSwatchSize = 56.dp

private const val KEPT_SATURATION = 0.3f
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f
private val HairlineWidth = 1.dp
private val BadgeSize = 18.dp
private val BadgeCheckSize = 12.dp
private val BadgeRise = 2.dp

private const val SPLIT_AT = 0.55f
private const val CHECK_RATIO = 0.4f
private val RingWidth = 2.dp
private val RingInset = 4.dp
