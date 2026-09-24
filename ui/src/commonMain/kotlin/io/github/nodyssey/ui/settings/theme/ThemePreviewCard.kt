package io.github.nodyssey.ui.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.settings_theme_preview_accent
import io.github.nodyssey.ui.resources.settings_theme_preview_board
import io.github.nodyssey.ui.resources.settings_theme_preview_meta
import io.github.nodyssey.ui.resources.settings_theme_preview_primary
import io.github.nodyssey.ui.resources.settings_theme_preview_secondary
import io.github.nodyssey.ui.resources.settings_theme_preview_title
import io.github.plaza.designsys.component.LayerCardShape
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.TonalTagShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.cardShadow
import org.jetbrains.compose.resources.stringResource

/**
 * A miniature of the app, drawn in the scheme the settings above it produce.
 *
 * The point of it is the roles, not the pixels: a seed reaches the reader as `primary` on a button,
 * `secondaryContainer` under a board tag and `tertiaryContainer` on the one accent a thread has, and
 * a row of bare swatches never showed which of those a colour was about to become.
 *
 * It reads the ambient `MaterialTheme` rather than generating a scheme of its own. Every control on
 * this screen writes straight through to the store and the whole app re-themes on the next frame —
 * so the ambient scheme *is* the answer, including under 动态取色, where the system's palette cannot
 * be regenerated from a seed at all.
 *
 * Type is in literal `sp` rather than from the type scale: this is a picture of an app, and a
 * picture that grew with 正文字号 would stop fitting its own frame.
 */
@Composable
internal fun ThemePreviewCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val layers = LocalPlazaLayers.current
    val shape = LayerCardShape
    // A card like every card in the feed, on the page like they are: the preview is a post as the
    // home list draws it, so it takes the same white, the same shadow and, on 墨水屏, the same rule.
    Surface(
        modifier = modifier.cardShadow(shape, layers.shadows).fillMaxWidth().clearAndSetSemantics {},
        color = layers.card,
        shape = shape,
        border = layers.cardBorderStroke,
    ) {
        Column {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(Res.string.settings_theme_preview_board),
                        style = TextStyle(fontSize = 10.5.sp),
                        color = scheme.onSecondaryContainer,
                        modifier =
                        Modifier
                            .clip(TonalTagShape)
                            .background(scheme.secondaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Text(
                        stringResource(Res.string.settings_theme_preview_meta),
                        style = TextStyle(fontSize = 10.5.sp),
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(Res.string.settings_theme_preview_title),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PreviewStat(Icons.Default.ThumbUp, "12", scheme.onSurfaceVariant)
                    PreviewStat(PlazaIcons.ModeComment, "28", scheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewPill(
                    stringResource(Res.string.settings_theme_preview_primary),
                    scheme.primary,
                    scheme.onPrimary,
                    FontWeight.SemiBold,
                )
                PreviewPill(
                    stringResource(Res.string.settings_theme_preview_secondary),
                    scheme.secondaryContainer,
                    scheme.onSecondaryContainer,
                    FontWeight.Medium,
                )
                PreviewPill(
                    stringResource(Res.string.settings_theme_preview_accent),
                    scheme.tertiaryContainer,
                    scheme.onTertiaryContainer,
                    FontWeight.Medium,
                    horizontal = 14.dp,
                )
            }
        }
    }
}

@Composable
private fun PreviewStat(
    icon: ImageVector,
    count: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(count, style = TextStyle(fontSize = 11.sp), color = tint)
    }
}

@Composable
private fun PreviewPill(
    label: String,
    container: Color,
    content: Color,
    weight: FontWeight,
    horizontal: Dp = 16.dp,
) {
    Text(
        label,
        style = TextStyle(fontSize = 12.5.sp, fontWeight = weight),
        color = content,
        maxLines = 1,
        modifier =
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = horizontal)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
