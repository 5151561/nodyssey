package io.github.nsreader.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The four Material Symbols the design uses that `material-icons-core` does not ship.
 *
 * Declared here rather than pulling in `material-icons-extended`, which would add roughly two
 * thousand unused vectors — and, more to the point, a dependency-graph change that this project's
 * strict lockfile makes a separate, deliberate operation.
 */
object NodeSeekIcons {
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
