package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * A determinate bar when the number is known, an empty track when it is not. Never a guessed fill.
 *
 * Shared by the level bar and the four allowance bars on 账户与成长, the level bar on 我的 and the
 * balance header of 鸡腿流水, so the four places a chicken count is drawn as progress cannot disagree
 * about what a bar looks like. The gap and the stop indicator Material draws by default are turned
 * off: a dot at the far right would look like a value the site never published.
 */
@Composable
internal fun GrowthProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearProgressIndicator(
        progress = { progress ?: 0f },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(6.dp),
    )
}
