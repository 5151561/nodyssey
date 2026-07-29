package io.github.nodyssey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

/*
 * The three pieces every scrolling list in this app repeats.
 *
 * Shared rather than copied because all three are pure appearance: a container colour or a type scale
 * that drifts on one screen and not the other is the kind of difference nobody notices in review and
 * everybody notices side by side. The feed and the thread had byte-identical copies of the skeleton
 * bar and near-identical copies of the meta line, which is how the drift starts.
 */

/** One grey bar of a loading skeleton. [fraction] is the share of the width it fills. */
@Composable
fun SkeletonBar(
    fraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/**
 * A line of row metadata — author, counts, timestamps.
 *
 * Tabular figures so the numbers in a column line up rather than shimmying as they change.
 *
 * [singleLine] is a parameter rather than a default because the two callers genuinely differ: the feed
 * packs several of these into one row where a long author name has to ellipsize, while the thread
 * header shows one timestamp that should never be cut. Making one of those the silent default would
 * change the other's behaviour on the next edit.
 */
@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier,
    )
}

/**
 * The spinner a paged list shows under its last row while the next page loads.
 *
 * Deliberately still a `CircularProgressIndicator` and not the Expressive `LoadingIndicator`: at
 * 22dp the shape-morphing indicator reads as a wobble rather than as progress, and this one sits in a
 * footer the eye passes over. The full-screen loading states use `LoadingState` for that reason.
 */
@Composable
fun AppendSpinner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(APPEND_SPINNER_SIZE))
    }
}

/** The size every in-list spinner uses, whatever container it sits in. */
val APPEND_SPINNER_SIZE = 22.dp
