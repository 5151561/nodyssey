package io.github.nsreader.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Material Symbols the design uses that `material-icons-core` does not ship.
 *
 * Declared here rather than pulling in `material-icons-extended`, which would add roughly two
 * thousand unused vectors — and, more to the point, a dependency-graph change that this project's
 * strict lockfile makes a separate, deliberate operation.
 *
 * The bar for adding one: the icon has to carry meaning a core icon cannot. 账号设置's `tune` became
 * `Icons.Default.Settings` and `logout` became `ExitToApp` for exactly that reason, and the signature
 * editor's format toolbar uses the same text glyphs the post composer already does.
 */
object NodeSeekIcons {
    val History: ImageVector by lazy {
        materialIcon(
            name = "History",
            pathData =
            "M13,3a9,9 0,0 0,-9,9H1l4,4 4,-4H6a7,7 0,1 1,2.05,4.95l-1.42,1.42A9,9 0,1 0,13,3z" +
                "m-1,5v5l4.25,2.52.75,-1.23 -3.5,-2.04V8z",
        )
    }

    val PersonSearch: ImageVector by lazy {
        materialIcon(
            name = "PersonSearch",
            pathData =
            "M10,8a4,4 0,1 1,-8,0 4,4 0,0 1,8,0zM6,14c-2.67,0 -6,1.34 -6,4v2h9.35" +
                "A7,7 0,0 1,9,18c0,-1.38 .4,-2.66 1.1,-3.74A16,16 0,0 0,6,14z" +
                "M16,12a3,3 0,1 0,1.66,5.49L20.17,20 21,19.17l-2.51,-2.51A3,3 0,0 0,16,12z" +
                "M16,14a1,1 0,1 1,0,2 1,1 0,0 1,0,-2z",
        )
    }

    /** Reply action used by the detail screen's sole primary FAB. */
    val Reply: ImageVector by lazy {
        materialIcon(
            name = "Reply",
            pathData =
            "M10,9V5L3,12l7,7v-4.1c5,0 8.5,1.6 11,5.1-1,-5-4,-11-11,-11z",
        )
    }

    /** Edit action used by the profile header without pulling in material-icons-extended. */
    val Edit: ImageVector by lazy {
        materialIcon(
            name = "Edit",
            pathData =
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
                "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        )
    }

    /** Sort order — the only action in the home app bar. */
    val SwapVert: ImageVector by lazy {
        materialIcon(
            name = "SwapVert",
            pathData = "M16,17.01L16,10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3L8,14h2L10,6.99h3L9,3z",
        )
    }

    /** Marks a pinned thread in the list. */
    val PushPin: ImageVector by lazy {
        materialIcon(
            name = "PushPin",
            pathData =
            "M16,9V4l1,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1l1,0v5" +
                "c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2c-1.66,0 -3,-1.34 -3,-3z",
        )
    }

    /** View count on the detail screen. */
    val Visibility: ImageVector by lazy {
        materialIcon(
            name = "Visibility",
            pathData =
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5" +
                "c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5" +
                " -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    /** Copies a code block, which is most of what people come to this forum for. */
    val ContentCopy: ImageVector by lazy {
        materialIcon(
            name = "ContentCopy",
            pathData =
            "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11" +
                "c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z",
        )
    }

    /** Chicken-leg reward. Kept local because material-icons-core has no matching symbol. */
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

    val ThumbDown: ImageVector by lazy {
        materialIcon(
            name = "ThumbDown",
            pathData =
            "M15,3H6c-0.8,0 -1.5,0.5 -1.8,1.2L1.1,11.3C0.6,12.6 1.5,14 3,14h5.7" +
                "l-1,4.6C7.5,19.4 8.1,20 8.8,20h0.4c0.4,0 0.8,-0.2 1.1,-0.5L16,13.8V5" +
                "c0,-1.1 -0.9,-2 -2,-2zM18,3v11h4V3z",
        )
    }

    /** Bio — the one-line self-introduction, on the 账号设置 list. */
    val Badge: ImageVector by lazy {
        materialIcon(
            name = "Badge",
            pathData =
            "M20,7h-5V4c0,-1.1 -0.9,-2 -2,-2h-2c-1.1,0 -2,0.9 -2,2v3H4C2.9,7 2,7.9 2,9v11" +
                "c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V9C22,7.9 21.1,7 20,7zM11,4h2v3h-2V4z" +
                "M12,12.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5s-1.5,-0.67 -1.5,-1.5" +
                "S11.17,12.5 12,12.5zM15,18H9v-0.75c0,-1 1.34,-1.81 3,-1.81s3,0.81 3,1.81V18z",
        )
    }

    /** Readme — the long Markdown block shown on a user's page. */
    val Article: ImageVector by lazy {
        materialIcon(
            name = "Article",
            pathData =
            "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
                "M14,17H7v-2h7V17zM17,13H7v-2h10V13zM17,9H7V7h10V9z",
        )
    }

    /** Two-factor authentication. */
    val Shield: ImageVector by lazy {
        materialIcon(
            name = "Shield",
            pathData = "M12,1L3,5v6c0,5.55 3.84,10.74 9,12c5.16,-1.26 9,-6.45 9,-12V5L12,1z",
        )
    }

    /** The blocked-user list, and the empty state that goes with it. */
    val Block: ImageVector by lazy {
        materialIcon(
            name = "Block",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10s10,-4.48 10,-10S17.52,2 12,2z" +
                "M4,12c0,-4.42 3.58,-8 8,-8c1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12z" +
                "M12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12C20,16.42 16.42,20 12,20z",
        )
    }

    /** Which boards the home strip shows. */
    val DashboardCustomize: ImageVector by lazy {
        materialIcon(
            name = "DashboardCustomize",
            pathData =
            "M3,11h8V3H3V11zM5,5h4v4H5V5zM13,3v8h8V3H13zM19,9h-4V5h4V9zM3,21h8v-8H3V21z" +
                "M5,15h4v4H5V15zM18,13h-2v3h-3v2h3v3h2v-3h3v-2h-3V13z",
        )
    }

    /** The struck-through eye on a password field. Pairs with [Visibility]. */
    val VisibilityOff: ImageVector by lazy {
        materialIcon(
            name = "VisibilityOff",
            pathData =
            "M12,7c2.76,0 5,2.24 5,5c0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92" +
                "c1.51,-1.26 2.7,-2.89 3.43,-4.75c-1.73,-4.39 -6,-7.5 -11,-7.5c-1.4,0 -2.74,0.25 -3.98,0.7" +
                "l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28l0.46,0.46C3.08,8.3 1.78,10.02 1,12" +
                "c1.73,4.39 6,7.5 11,7.5c1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22L21,20.73L3.27,3L2,4.27z" +
                "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65c0,1.66 1.34,3 3,3c0.22,0 0.44,-0.03 0.65,-0.08" +
                "l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53c-2.76,0 -5,-2.24 -5,-5C7,11.21 7.2,10.47 7.53,9.8z" +
                "M11.84,9.02l3.15,3.15l0.02,-0.16c0,-1.66 -1.34,-3 -3,-3L11.84,9.02z",
        )
    }

    /** Take a new avatar photo. */
    val PhotoCamera: ImageVector by lazy {
        materialIcon(
            name = "PhotoCamera",
            pathData =
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
                "M9,2L7.17,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6" +
                "c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5" +
                "s5,2.24 5,5S14.76,17 12,17z",
        )
    }

    /** Pick an avatar from the photo library. */
    val Image: ImageVector by lazy {
        materialIcon(
            name = "Image",
            pathData =
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
                "M8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
        )
    }

    /** "Awaiting verification" on a freshly changed email address. */
    val Schedule: ImageVector by lazy {
        materialIcon(
            name = "Schedule",
            pathData =
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8s8,3.58 8,8S16.42,20 12,20z" +
                "M12.5,7H11v6l5.25,3.15l0.75,-1.23l-4.5,-2.67z",
        )
    }

    /** Bind an authenticator app by scanning a code. */
    val QrCode: ImageVector by lazy {
        materialIcon(
            name = "QrCode",
            pathData =
            "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13z" +
                "M19,9h-4V5h4V9zM19,19h2v2h-2V19zM13,13h2v2h-2V13zM15,15h2v2h-2V15z" +
                "M13,17h2v2h-2V17zM15,19h2v2h-2V19zM17,17h2v2h-2V17zM17,13h2v2h-2V13zM19,15h2v2h-2V15z",
        )
    }
}

private fun materialIcon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            // Black is a placeholder: `Icon` tints the whole vector, so the fill never survives.
            fill = SolidColor(Color.Black),
        ).build()
