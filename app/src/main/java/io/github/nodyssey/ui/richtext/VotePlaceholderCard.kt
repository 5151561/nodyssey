package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.plaza.designsys.component.NodysseyIcons
import io.github.plaza.designsys.theme.NodysseyTheme
import io.github.plaza.designsys.theme.Spacing

/**
 * What a vote looks like where it cannot be played with.
 *
 * [RichContent] renders in four places that have no ViewModel and no business issuing requests —
 * editor previews, signatures, direct messages, space readmes — so the slot's default has to say
 * "there is a vote here" without going and getting it. The thread screen is the one caller that
 * substitutes a live card.
 *
 * Also what the reader sees for a body restored from cache with no network: the placeholder is in
 * the stored article, the vote never was.
 */
@Composable
fun VotePlaceholderCard(modifier: Modifier = Modifier) {
    VoteCardSurface(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                NodysseyIcons.Poll,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    stringResource(R.string.vote_placeholder_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.vote_placeholder_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The container every vote state shares.
 *
 * Kept here rather than in `ui/vote` because the placeholder above is the renderer's own fallback and
 * has to look like the real thing minus the controls — two surfaces drifting apart would make a
 * cached body read as a different kind of object from a live one.
 */
@Composable
fun VoteCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(Spacing.md)) { content() }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VotePlaceholderCardPreview() {
    NodysseyTheme {
        VotePlaceholderCard(Modifier.padding(Spacing.lg))
    }
}
