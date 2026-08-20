package io.github.nodyssey.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.materialIcon

/**
 * Icons that only mean something on NodeSeek.
 *
 * Kept out of `PlazaIcons` on purpose: that object is the shared set, and a glyph for this site's
 * currency has nothing to say to another forum. Drawn through the same helper so it matches the rest
 * at 24dp.
 */
object NodeSeekIcons {
    /** 鸡腿, the reward NodeSeek pays for a post. No core symbol comes close. */
    val ChickenLeg: ImageVector by lazy {
        materialIcon(
            name = "ChickenLeg",
            pathData =
            "M14.5,3.2c-2.7,-1.4 -6.3,-0.2 -8.2,2.5c-2,2.9 -1.7,6.3 0.6,8.1" +
                "c2.3,1.8 5.7,1.2 7.6,-1.5c1.9,-2.7 2.7,-7.8 0,-9.1z" +
                "M14.1,13.2l2.8,2.8c0.8,-0.4 1.8,-0.3 2.4,0.4c0.9,0.9 0.9,2.3 0,3.2" +
                "c-0.9,0.9 -2.3,0.9 -3.2,0c-0.7,-0.7 -0.8,-1.6 -0.4,-2.4l-2.8,-2.8z",
        )
    }

    /**
     * 推荐阅读 — the diamond NodeSeek stamps on an 加精 thread, drawn from the site's own sprite.
     *
     * Copied out of the live `<symbol id="diamonds">` (read 2026-08-17) rather than approximated, so
     * the badge in a list row is the glyph the reader already knows from the web. That symbol is a
     * stroked outline on a 48×48 viewport, which is why this one icon does not go through
     * [materialIcon] — that helper fills a 24×24 path, and filling this one would mean redrawing the
     * gem's facets by hand. The sprite's second path is dropped: every one of its segments lies on an
     * edge the first path already strokes, so it changes nothing on screen.
     */
    val Award: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Award",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 48f,
                viewportHeight = 48f,
            ).addPath(
                pathData = addPathNodes("M12 8h24l8 10-20 24L4 18l8-10ZM4 18h40M24 42l-8-24m8 24 8-24"),
                // Black is a placeholder for the same reason as in `materialIcon`: `Icon` tints the
                // whole vector, stroke included, so nothing of this colour survives.
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
    }
}
