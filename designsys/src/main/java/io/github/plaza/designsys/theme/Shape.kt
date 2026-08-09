package io.github.plaza.designsys.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The Material 3 shape scale, at the radii the design doc specifies.
 *
 * The four in-between slots — `largeIncreased` (20.dp) and friends — are left at their Material
 * defaults, which already match the design doc; read them as `MaterialTheme.shapes.largeIncreased`
 * rather than declaring a parallel constant.
 */
val PlazaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/**
 * The status-screen shape family.
 *
 * Every empty and error state puts its icon on a soft blob rather than a circle — that asymmetry is
 * most of what makes these screens read as *this* app. Each state gets its own corner rhythm so the
 * eight of them are distinguishable at a glance while staying one family.
 */
object StatusShapes {
    val Empty = RoundedCornerShape(topStartPercent = 42, topEndPercent = 58, bottomEndPercent = 40, bottomStartPercent = 60)
    val NetworkError = RoundedCornerShape(topStartPercent = 58, topEndPercent = 42, bottomEndPercent = 55, bottomStartPercent = 45)
    val Challenge = RoundedCornerShape(topStartPercent = 38, topEndPercent = 62, bottomEndPercent = 45, bottomStartPercent = 55)
    val SignIn = RoundedCornerShape(topStartPercent = 55, topEndPercent = 45, bottomEndPercent = 60, bottomStartPercent = 40)
    val Deleted = RoundedCornerShape(topStartPercent = 45, topEndPercent = 55, bottomEndPercent = 42, bottomStartPercent = 58)
    val NoResults = RoundedCornerShape(topStartPercent = 60, topEndPercent = 40, bottomEndPercent = 55, bottomStartPercent = 45)
    val Welcome = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 58, bottomStartPercent = 42)
    val Loading = RoundedCornerShape(percent = 50)
}
