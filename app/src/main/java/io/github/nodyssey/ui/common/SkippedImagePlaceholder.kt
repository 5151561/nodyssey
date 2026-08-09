package io.github.nodyssey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.plaza.designsys.component.NodysseyIcons
import io.github.plaza.designsys.theme.NodysseyTheme
import io.github.plaza.designsys.theme.Spacing

/**
 * Stands in for an image that 仅 Wi-Fi 加载图片 skipped, and loads it when tapped.
 *
 * A skipped image used to leave nothing behind: the post had a hole where a screenshot should be,
 * with no way to tell it from a post that never had one — and no way to see it short of turning the
 * preference off in 设置. The placeholder reserves the space, says why the image is not there, and is
 * itself the button that fetches it.
 */
@Composable
fun SkippedImagePlaceholder(
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PLACEHOLDER_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onLoad),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) {
            Icon(
                imageVector = NodysseyIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.image_skipped_wifi_only),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.image_skipped_tap_to_load),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Tall enough to read as a missing image rather than as a divider, short enough not to dominate. */
private val PLACEHOLDER_HEIGHT = 132.dp

@Preview(showBackground = true, widthDp = 360, name = "图片跳过占位")
@Composable
private fun SkippedImagePlaceholderPreview() {
    NodysseyTheme {
        SkippedImagePlaceholder(onLoad = {}, modifier = Modifier.padding(Spacing.lg))
    }
}
