package io.github.nodyssey.ui.notifications

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.PreviewPart
import io.github.nodyssey.data.PreviewPlaceholder
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.notification_preview_code
import io.github.nodyssey.ui.resources.notification_preview_image
import io.github.nodyssey.ui.resources.notification_preview_payment
import io.github.nodyssey.ui.resources.notification_preview_sticker
import io.github.nodyssey.ui.resources.notification_preview_table
import io.github.nodyssey.ui.resources.notification_preview_vote
import org.jetbrains.compose.resources.stringResource

/**
 * A flattened body as one line of the reader's own language, with the pictures named rather drawn.
 *
 * The placeholders are strings rather than the pictures they stand for on purpose: a row that draws
 * a sticker is a row of a different height, and a list whose rows change height as their images
 * arrive is a list that moves under the thumb. `[图片]` also survives being read aloud, which an
 * `<img>` in a list row does not.
 *
 * Shared by the notification rows and the 私信 list because both are one line about something
 * somebody wrote, and a `![](…png)` printed as written is no more use in one than in the other.
 */
@Composable
internal fun previewText(parts: List<PreviewPart>): String? {
    if (parts.isEmpty()) return null
    // Read before the loop rather than inside it: `stringResource` is a composable call and the
    // loop's body is not a composable scope, and a comment carrying five stickers would otherwise
    // read the same string five times.
    val labels = placeholderLabels()
    return buildString {
        parts.forEach { part ->
            when (part) {
                is PreviewPart.Text -> append(part.text)
                is PreviewPart.Placeholder -> append(labels.getValue(part.kind))
            }
        }
    }.ifBlank { null }
}

@Composable
private fun placeholderLabels(): Map<PreviewPlaceholder, String> =
    mapOf(
        PreviewPlaceholder.IMAGE to stringResource(Res.string.notification_preview_image),
        PreviewPlaceholder.STICKER to stringResource(Res.string.notification_preview_sticker),
        PreviewPlaceholder.CODE to stringResource(Res.string.notification_preview_code),
        PreviewPlaceholder.TABLE to stringResource(Res.string.notification_preview_table),
        PreviewPlaceholder.VOTE to stringResource(Res.string.notification_preview_vote),
        PreviewPlaceholder.PAYMENT to stringResource(Res.string.notification_preview_payment),
    )
