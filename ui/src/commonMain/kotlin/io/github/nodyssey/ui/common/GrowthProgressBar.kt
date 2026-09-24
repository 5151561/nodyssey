package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.nodyssey.core.LevelProgress
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.assets_quota_value
import io.github.nodyssey.ui.resources.level_progress_remaining
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/**
 * A determinate bar when the number is known, an empty track when it is not. Never a guessed fill.
 *
 * Shared by the level bar ([LevelProgressLine]) and the four allowance bars on 账户与成长, so every
 * place a chicken count is drawn as progress agrees about what a bar looks like. The gap and the stop
 * indicator Material draws by default are turned off: a dot at the far right would look like a value
 * the site never published.
 */
@Composable
internal fun GrowthProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress ?: 0f },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(6.dp),
    )
}

/**
 * A level's bar with its one caption under it — 「344 / 400 · 还差 56 升到 Lv2」, or only 「344 / 400」
 * once the count has reached the next level.
 *
 * 我的, 账户与成长 and 鸡腿流水 all draw the level this way, so the three cannot word the same fact
 * differently.
 */
@Composable
internal fun LevelProgressLine(
    progress: LevelProgress,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GrowthProgressBar(progress = progress.fraction)
        Text(
            text =
            progress.remaining?.let {
                stringResource(Res.string.level_progress_remaining, progress.chicken, progress.span.next, it, progress.nextRank)
            } ?: stringResource(Res.string.assets_quota_value, progress.chicken, progress.span.next),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
