package io.github.nodyssey.ui.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One colour dot, in the three places j1 draws one: the preset grid, the wallpaper candidates, and
 * the swatch beside a saved theme's name.
 *
 * The selected state is a ring standing off the dot rather than a border on it — 2dp of ground, then
 * 2dp of `primary` — which is what keeps a dark seed from losing its edge against a dark surface.
 * The ground is painted rather than left transparent because the ring's gap has to be
 * `surfaceContainerLow` even where the dot sits on the preset grid's `surfaceContainer` plate.
 *
 * [second] is the dot's other half, split on the 135° diagonal. Preset dots carry one because a
 * scheme is a pair of families and a single circle only ever showed half of what the tap would do.
 *
 * The dot is only the target where it is the whole cell. In the preset grid the name and the hex
 * under it belong to the same choice, so the target is the cell and [onClick] is left null — a 56dp
 * circle with a label beside it that does nothing when tapped is a control people report as broken.
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

/** 44dp: the wallpaper candidates, and Material's minimum for something you have to hit. */
internal val SwatchSize = 44.dp

/** 56dp, the preset grid's — it carries a label and a hex under it and can afford the room. */
internal val PresetSwatchSize = 56.dp

private const val SPLIT_AT = 0.55f
private const val CHECK_RATIO = 0.4f
private val RingWidth = 2.dp
private val RingInset = 4.dp
