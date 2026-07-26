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

    /** Sends a direct message (board 7f). */
    val Send: ImageVector by lazy {
        materialIcon(
            name = "Send",
            pathData = "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z",
        )
    }

    /** Starts a conversation from the message list's FAB (board 7e). */
    val AddComment: ImageVector by lazy {
        materialIcon(
            name = "AddComment",
            pathData =
            "M22,4c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4z" +
                "M17,11h-4v4h-2v-4H7V9h4V5h2v4h4v2z",
        )
    }

    /** A message that failed to send (board 7f). */
    val ErrorCircle: ImageVector by lazy {
        materialIcon(
            name = "ErrorCircle",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "m1,15h-2v-2h2v2zm0,-4h-2L11,7h2v6z",
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

    // --- Editor toolbar (boards 6d / 7a / c5 / c6) ---------------------------

    val FormatBold: ImageVector by lazy {
        materialIcon(
            name = "FormatBold",
            pathData =
            "M15.6,10.79c0.97,-0.67 1.65,-1.77 1.65,-2.79c0,-2.26 -1.75,-4 -4,-4H7v14h7.04" +
                "c2.09,0 3.71,-1.7 3.71,-3.79c0,-1.52 -0.86,-2.82 -2.15,-3.42z" +
                "M10,6.5h3c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5h-3V6.5z" +
                "M13.5,15.5H10v-3h3.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5z",
        )
    }

    /** Heading. Material's `title` glyph rather than `format_h2`, which is text rendered as a path. */
    val Title: ImageVector by lazy {
        materialIcon(name = "Title", pathData = "M5,4v3h5.5v12h3V7H19V4z")
    }

    val Code: ImageVector by lazy {
        materialIcon(
            name = "Code",
            pathData =
            "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4z" +
                "M14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z",
        )
    }

    val FormatQuote: ImageVector by lazy {
        materialIcon(
            name = "FormatQuote",
            pathData = "M6,17h3l2,-4V7H5v6h3zM14,17h3l2,-4V7h-6v6h3z",
        )
    }

    val FormatListBulleted: ImageVector by lazy {
        materialIcon(
            name = "FormatListBulleted",
            pathData =
            "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5z" +
                "M4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5z" +
                "M4,16.5c-0.83,0 -1.5,0.68 -1.5,1.5s0.68,1.5 1.5,1.5 1.5,-0.68 1.5,-1.5 -0.67,-1.5 -1.5,-1.5z" +
                "M7,19h14v-2H7v2zM7,13h14v-2H7v2zM7,5v2h14V5H7z",
        )
    }

    val Link: ImageVector by lazy {
        materialIcon(
            name = "Link",
            pathData =
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1" +
                "s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
        )
    }

    val Image: ImageVector by lazy {
        materialIcon(
            name = "Image",
            pathData =
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
                "M8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
        )
    }

    /** The emoji panel's entry point. */
    val Mood: ImageVector by lazy {
        materialIcon(
            name = "Mood",
            pathData =
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
                "M15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8 14,8.67 14,9.5s0.67,1.5 1.5,1.5z" +
                "M8.5,11c0.83,0 1.5,-0.67 1.5,-1.5S9.33,8 8.5,8 7,8.67 7,9.5 7.67,11 8.5,11z" +
                "M12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89c0.8,2.04 2.78,3.5 5.11,3.5z",
        )
    }

    /** Mentions another member; the reply editor's `@`. */
    val AlternateEmail: ImageVector by lazy {
        materialIcon(
            name = "AlternateEmail",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10h5v-2h-5c-4.34,0 -8,-3.66 -8,-8s3.66,-8 8,-8 8,3.66 8,8" +
                "v1.43c0,0.79 -0.71,1.57 -1.5,1.57s-1.5,-0.78 -1.5,-1.57V12c0,-2.76 -2.24,-5 -5,-5s-5,2.24 -5,5" +
                " 2.24,5 5,5c1.38,0 2.64,-0.56 3.54,-1.47 0.65,0.89 1.77,1.47 2.96,1.47 1.97,0 3.5,-1.6 3.5,-3.57" +
                "V12c0,-5.52 -4.48,-10 -10,-10zM12,15c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3z",
        )
    }

    /** The emoji panel's delete key. */
    val Backspace: ImageVector by lazy {
        materialIcon(
            name = "Backspace",
            pathData =
            "M22,3H7c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15" +
                "c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12" +
                " 9,8.41 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12 19,15.59z",
        )
    }

    /** The forum rules card on the publish preview. */
    val Campaign: ImageVector by lazy {
        materialIcon(
            name = "Campaign",
            pathData =
            "M18,11v2h4v-2h-4zM16,17.61c0.96,0.71 2.21,1.65 3.2,2.39 0.4,-0.53 0.8,-1.07 1.2,-1.6" +
                " -0.99,-0.74 -2.24,-1.68 -3.2,-2.4 -0.4,0.54 -0.8,1.08 -1.2,1.61z" +
                "M20.4,5.6c-0.4,-0.53 -0.8,-1.07 -1.2,-1.6 -0.99,0.74 -2.24,1.68 -3.2,2.4 0.4,0.53 0.8,1.07 1.2,1.6" +
                " 0.96,-0.72 2.21,-1.65 3.2,-2.4zM4,9c-1.1,0 -2,0.9 -2,2v2c0,1.1 0.9,2 2,2h1v4h2v-4h1l5,3V6L8,9H4z" +
                "M15.5,12c0,-1.33 -0.58,-2.53 -1.5,-3.35v6.69c0.92,-0.81 1.5,-2.01 1.5,-3.34z",
        )
    }

    /** Draft recovery. */
    val Drafts: ImageVector by lazy {
        materialIcon(
            name = "Drafts",
            pathData =
            "M21.99,8c0,-0.72 -0.37,-1.35 -0.94,-1.7L12,1 2.95,6.3C2.38,6.65 2,7.28 2,8v10c0,1.1 0.9,2 2,2h16" +
                "c1.1,0 2,-0.9 2,-2l-0.01,-10zM12,13L3.74,7.84 12,3l8.26,4.84L12,13z",
        )
    }

    /** An attachment that has not started uploading yet. */
    val Schedule: ImageVector by lazy {
        materialIcon(
            name = "Schedule",
            pathData =
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
                "M12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
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
