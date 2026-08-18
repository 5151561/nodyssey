package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

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
 * A counted meta item drawn as an icon and a number — 回复数, 浏览数.
 *
 * The words those numbers used to carry cost about six characters of a 286dp meta line, which on a
 * 360dp phone was the difference between one line and two: a real author name pushed the timestamp
 * onto a second row, and a meta line that is sometimes one row and sometimes two is exactly the
 * "nothing lines up" the list reads as. An icon says the same thing in a third of the width, and
 * says it without being read.
 *
 * The icon is sized in `sp`, not `dp`, and applies the in-app reading-size preference on top, so it
 * grows with the number beside it under either — rather than shrinking into it as the type does.
 *
 * The whole pair carries one [contentDescription] and clears what is under it, so a screen reader
 * says "12 回复" once instead of announcing a bare number next to a nameless glyph.
 */
@Composable
fun MetaStat(
    icon: ImageVector,
    value: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val iconSize = textScaledSize(META_STAT_ICON_SIZE)
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The `dp` an icon has to be to keep up with the text beside it.
 *
 * Both scales at once: Android's, which `sp` carries on its own, and the app's own reading-size
 * preference, which lives in the type scale and so has to be applied by hand to anything that is
 * not text. An icon that answers to only one of the two falls behind its own label under the other.
 */
@Composable
fun textScaledSize(size: TextUnit): Dp {
    val scale = LocalPlazaFontScale.current
    return with(LocalDensity.current) { (size * scale).toDp() }
}

/**
 * A hair under the 12sp the number is set in.
 *
 * Matching the type size exactly makes the glyph the heavier of the two — an icon fills its box
 * where a digit does not — so it is stepped down until the pair reads as one weight.
 */
private val META_STAT_ICON_SIZE = 13.sp

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
