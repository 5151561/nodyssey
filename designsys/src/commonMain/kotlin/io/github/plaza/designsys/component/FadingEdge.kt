package io.github.plaza.designsys.component

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fades the end edge of a horizontally scrolling row out while there is more of it past that edge.
 *
 * Goes ahead of `horizontalScroll` in the chain, so that it masks the viewport rather than the
 * content scrolling under it. The fade deepens over the last [length] of scroll rather than switching
 * off at the end, so reaching the end reads as the edge clearing rather than a flicker.
 *
 * Hand-drawn: foundation 1.13.0-alpha02 has no fading-edge modifier for a scroll container (its
 * classes were searched for one, 2026-10-08). Remove this when it grows one.
 */
fun Modifier.fadingEndEdge(
    scrollState: ScrollState,
    length: Dp = 48.dp,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = length.toPx()
            val strength = ((scrollState.maxValue - scrollState.value) / edge).coerceIn(0f, 1f)
            if (strength == 0f) return@drawWithContent
            val ltr = layoutDirection == LayoutDirection.Ltr
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - strength),
                    startX = if (ltr) size.width - edge else edge,
                    endX = if (ltr) size.width else 0f,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
