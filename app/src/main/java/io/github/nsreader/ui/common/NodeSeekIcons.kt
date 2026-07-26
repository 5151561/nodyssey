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

    /** Curated reading list. */
    val MenuBook: ImageVector by lazy {
        materialIcon(
            name = "MenuBook",
            pathData =
            "M21,5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5" +
                "c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5" +
                "c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5" +
                "c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05" +
                "c0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1z" +
                "M21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.7,0 -4.15,0.65 -5.5,1.5V8" +
                "c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z",
        )
    }

    /** Lucky draw — the site's T-floor notary tool. */
    val Casino: ImageVector by lazy {
        materialIcon(
            name = "Casino",
            pathData =
            "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M7.5,18C6.67,18 6,17.33 6,16.5S6.67,15 7.5,15 9,15.67 9,16.5 8.33,18 7.5,18z" +
                "M7.5,9C6.67,9 6,8.33 6,7.5S6.67,6 7.5,6 9,6.67 9,7.5 8.33,9 7.5,9z" +
                "M12,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z" +
                "M16.5,18c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z" +
                "M16.5,9c-0.83,0 -1.5,-0.67 -1.5,-1.5S15.67,6 16.5,6 18,6.67 18,7.5 17.33,9 16.5,9z",
        )
    }

    /** Invite code. */
    val ConfirmationNumber: ImageVector by lazy {
        materialIcon(
            name = "ConfirmationNumber",
            pathData =
            "M22,10V6c0,-1.1 -0.9,-2 -2,-2H4C2.9,4 2.01,4.9 2.01,6v4C3.11,10 4,10.9 4,12s-0.89,2 -2,2v4" +
                "c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2v-4c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2z" +
                "M13,17.5h-2v-2h2v2zM13,13h-2v-2h2v2zM13,8.5h-2v-2h2v2z",
        )
    }

    /** Moderation log. */
    val Gavel: ImageVector by lazy {
        materialIcon(
            name = "Gavel",
            pathData =
            "M2,20h12v2H2zM14.5,2.5l-3,3 6,6 3,-3zM10.4,6.6l-6.5,6.5 3,3 6.5,-6.5z",
        )
    }

    /** Balances and the transfer sheet. */
    val Wallet: ImageVector by lazy {
        materialIcon(
            name = "Wallet",
            pathData =
            "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14" +
                "c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9z" +
                "M12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5" +
                " -0.67,1.5 -1.5,1.5z",
        )
    }

    /** Two-factor authentication. */
    val Shield: ImageVector by lazy {
        materialIcon(
            name = "Shield",
            pathData =
            "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z" +
                "M12,11.99h7c-0.53,4.12 -3.28,7.79 -7,8.94V12H5V6.3l7,-3.11v8.8z",
        )
    }

    /** Blocked users. */
    val Block: ImageVector by lazy {
        materialIcon(
            name = "Block",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12z" +
                "M12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z",
        )
    }

    /** Saves a viewed image to the gallery. */
    val Download: ImageVector by lazy {
        materialIcon(
            name = "Download",
            pathData = "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z",
        )
    }

    /** Generated links, friend sites. */
    val Link: ImageVector by lazy {
        materialIcon(
            name = "Link",
            pathData =
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2z" +
                "M17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
        )
    }

    /** Leaves the app for the site. */
    val OpenInNew: ImageVector by lazy {
        materialIcon(
            name = "OpenInNew",
            pathData =
            "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7z" +
                "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
        )
    }

    /** Follows and followers. */
    val Group: ImageVector by lazy {
        materialIcon(
            name = "Group",
            pathData =
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
                "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
                "M8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
                "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5" +
                "c0,-2.33 -4.67,-3.5 -7,-3.5z",
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
