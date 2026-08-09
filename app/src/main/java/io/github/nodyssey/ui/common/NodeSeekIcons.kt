package io.github.nodyssey.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
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
}
