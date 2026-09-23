package io.github.nodyssey.ui.notifications

import androidx.compose.ui.graphics.vector.ImageVector
import io.github.plaza.designsys.component.materialIcon

/**
 * The three Material Symbols boards 5a and 3e use that neither `material-icons-core` nor
 * `PlazaIcons` ships.
 *
 * Kept beside the screens that draw them rather than added to `PlazaIcons`: nothing else in the app
 * asks for them, and that object is the design system's vocabulary, not a place for one screen's
 * glyphs. Move one there the day a second screen wants it.
 */
internal object InboxIcons {
    /** 全部已读 — `done_all`, the double tick the artboard puts in front of the action. */
    val DoneAll: ImageVector by lazy {
        materialIcon(
            name = "DoneAll",
            pathData =
            "M18,7l-1.41,-1.41 -6.34,6.34 1.41,1.41L18,7z" +
                "M22.24,5.59L11.66,16.17 7.48,12l-1.41,1.41L11.66,19l12,-12 -1.42,-1.41z" +
                "M0.41,13.41L6,19l1.41,-1.41L1.83,12 0.41,13.41z",
        )
    }

    /** 相册 in the message bar's tool grid — `photo_library`, a stack of pictures. */
    val PhotoLibrary: ImageVector by lazy {
        materialIcon(
            name = "PhotoLibrary",
            pathData =
            "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2z" +
                "M11,12l2.03,2.71L16,11l4,5H8l3,-4z" +
                "M2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
        )
    }

    /** The MD switch's tile — a framed `M` and a down arrow, Markdown's own mark. */
    val Markdown: ImageVector by lazy {
        materialIcon(
            name = "Markdown",
            pathData =
            "M20,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C22,4.9 21.1,4 20,4z" +
                "M20,18H4V6h16V18z" +
                "M5.5,15.5v-7H7l2,2.5 2,-2.5h1.5v7H11v-4.8l-2,2.5 -2,-2.5v4.8z" +
                "M16,8.5h1.5v4h2l-2.75,3.25 -2.75,-3.25h2z",
        )
    }
}
