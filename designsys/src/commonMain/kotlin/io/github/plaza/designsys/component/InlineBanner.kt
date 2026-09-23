package io.github.plaza.designsys.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A tinted message strip inside a page: an optional icon, an optional [title] over the [text], and an
 * optional [action] (a `TextButton`) at the trailing edge — a test result, a refusal, a failure with
 * its way out.
 *
 * Material 3 has no inline banner component (its `Snackbar` is transient and floats over the page), so
 * this is a [Surface] with a row in it. The colours are the caller's: an error, a success and a quiet
 * notice on the card colour are all the same object. [announce] makes it a polite live region, for a
 * banner that arrives after a wait the reader did not watch.
 */
@Composable
fun InlineBanner(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String? = null,
    border: BorderStroke? = null,
    announce: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = InlineBannerShape,
        border = border,
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ) {
        Row(
            modifier =
            Modifier
                .heightIn(min = 44.dp)
                .padding(start = 14.dp, end = if (action != null) 4.dp else 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    // With a title the icon sits beside it, not halfway down the body.
                    modifier =
                    Modifier
                        .then(if (title != null) Modifier.align(Alignment.Top).padding(top = 8.dp) else Modifier)
                        .size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                title?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            action?.invoke()
        }
    }
}

private val InlineBannerShape = RoundedCornerShape(16.dp)
