package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.richtext_image_load_failed
import io.github.plaza.designsys.resources.richtext_image_skipped_title
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The mark left where an image was expected and did not arrive.
 *
 * An image that fails silently is worse than one that fails loudly: `AsyncImage` draws nothing on
 * error, so a sticker vanished mid-sentence, a thumbnail left a hole in a row of them, and the
 * reader had no way to tell a picture that failed from a post that never had one. That is the same
 * complaint [SkippedImagePlaceholder] exists to answer, in the places too small to answer it with
 * words: this fills whatever box the caller gives it, says something is missing, and names which
 * kind of missing to a screen reader.
 *
 * [deferred] separates the two, because they are not the same fact and the fix for one is not the
 * fix for the other: 仅 Wi-Fi 加载图片 declining an image is the app's own choice — see
 * `ImagesDeferredException` — while a torn frame means the fetch was tried and failed.
 *
 * Sized entirely by [modifier]; the glyph follows. Where there is room for a sentence and a tap
 * target, use [SkippedImagePlaceholder] instead — this one is for the 20sp cases that have neither.
 */
@Composable
fun ImageFallback(
    modifier: Modifier = Modifier,
    deferred: Boolean = false,
) {
    val description =
        stringResource(
            if (deferred) Res.string.richtext_image_skipped_title else Res.string.richtext_image_load_failed,
        )
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (deferred) PlazaIcons.Image else PlazaIcons.BrokenImage,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            // A fraction rather than a fixed size: the same composable stands in for a 20sp sticker
            // and a 44dp list thumbnail, and a glyph that fills either one edge to edge reads as a
            // solid block rather than as a picture frame.
            modifier = Modifier.fillMaxSize(GLYPH_FRACTION),
        )
    }
}

/** How much of the frame the glyph takes, leaving the ground visible as a border on all four sides. */
private const val GLYPH_FRACTION = 0.72f

@Preview(showBackground = true, name = "图片占位")
@Composable
private fun ImageFallbackPreview() {
    PlazaTheme {
        ImageFallback(modifier = Modifier.size(44.dp))
    }
}

@Preview(showBackground = true, name = "图片占位·已跳过")
@Composable
private fun ImageFallbackDeferredPreview() {
    PlazaTheme {
        ImageFallback(modifier = Modifier.size(44.dp), deferred = true)
    }
}
