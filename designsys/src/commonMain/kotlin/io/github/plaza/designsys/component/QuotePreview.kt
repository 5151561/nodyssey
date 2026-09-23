package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers

/**
 * What a reply or a quote points at, in one line: a [leading] mark, whose words ([title], e.g.
 * 「轻舟 · #1」), the first line of them, and a ✕ when [onRemove] is given.
 *
 * On a [LayerCard] by default; [inset] recesses it into the surface instead, for a reference drawn
 * inside the thing it belongs to (the reply sheet). Laid out by hand rather than as a `ListItem`:
 * Material's list item enforces a 56dp one-line / 72dp two-line height and 16dp insets, twice the
 * weight of the one-line strip the artboards draw over a text field.
 */
@Composable
fun QuotePreview(
    title: String,
    excerpt: String?,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    inset: Boolean = false,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
) {
    val padding =
        PaddingValues(
            start = 12.dp,
            end = if (onRemove != null) 4.dp else 12.dp,
            top = if (onRemove != null) 4.dp else 12.dp,
            bottom = if (onRemove != null) 4.dp else 12.dp,
        )
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading()
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                excerpt?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            onRemove?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = removeLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (inset) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.largeIncreased,
            color = LocalPlazaLayers.current.inset,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box(Modifier.padding(padding)) { row() }
        }
    } else {
        LayerCard(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.largeIncreased,
            contentPadding = padding,
        ) {
            row()
        }
    }
}
