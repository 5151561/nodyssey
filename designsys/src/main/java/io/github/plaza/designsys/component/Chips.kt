package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small tonal label — a board name, a category, a tag.
 *
 * The colours are the caller's: which groups of things share a hue is a decision about a particular
 * forum's taxonomy, and no component can make it. What is shared is the shape, the type scale and the
 * padding, which is what keeps every tag in an app looking like the same object.
 *
 * Draws nothing for a blank [text], so a row holding one keeps its height while a page loads instead
 * of twitching when the tag arrives.
 */
@Composable
fun TonalTag(
    text: String?,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (text.isNullOrBlank()) return

    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = contentColor,
        modifier =
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/**
 * How loudly a [BadgeChip] speaks.
 *
 * Tones rather than colours, and deliberately fewer of them than a caller has categories: what a
 * badge *means* — 楼主, staff, banned — is the app's vocabulary, and mapping that vocabulary onto
 * these six is where the app says how much each one matters.
 */
enum class BadgeTone {
    /** The subject of the thing being read. */
    Primary,

    /** A standing worth noting, not a warning. */
    Accent,

    /** Present but spent: outlined rather than filled. */
    Muted,

    /** A warning about the person or the content. */
    Warning,

    /** The loudest one there is. Reserve it. */
    Critical,

    /** Anything unrecognised, which must render rather than crash an exhaustive `when`. */
    Neutral,
}

/** A filled (or, for [BadgeTone.Muted], outlined) badge next to a name. */
@Composable
fun BadgeChip(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            BadgeTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
            BadgeTone.Accent -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            BadgeTone.Muted -> Color.Transparent to scheme.onSurfaceVariant
            BadgeTone.Warning -> scheme.errorContainer to scheme.onErrorContainer
            BadgeTone.Critical -> scheme.error to scheme.onError
            BadgeTone.Neutral -> scheme.secondaryContainer to scheme.onSecondaryContainer
        }
    val shape = RoundedCornerShape(6.dp)
    val outlined =
        if (tone == BadgeTone.Muted) {
            Modifier.border(1.dp, scheme.outlineVariant, shape)
        } else {
            Modifier
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        modifier =
        modifier
            .clip(shape)
            .then(outlined)
            .background(container)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}
