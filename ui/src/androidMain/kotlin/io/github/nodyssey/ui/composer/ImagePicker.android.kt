package io.github.nodyssey.ui.composer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.nodyssey.data.composer.PickedImage

/**
 * `PickMultipleVisualMedia` — the system picker, so it needs no storage permission and shows nothing
 * but the images the user selects.
 */
@Composable
actual fun rememberImagePicker(
    maxItems: Int,
    fallbackName: String,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems),
        ) { uris -> onPicked(uris.toPickedImages(context, fallbackName)) }
    return {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/**
 * Turns what the photo picker returns into queue entries.
 *
 * The display name is worth the content-provider query: it is what the tray labels, what the
 * failure message quotes back, and what ends up as the image's alt text in the published Markdown.
 * Providers are allowed to answer with nothing, hence the two fallbacks.
 *
 * `fallbackName` is passed in rather than read here: since step D1 the strings are Compose
 * Resources, and the only way to reach one outside a composition is a `suspend` accessor.
 */
private fun List<Uri>.toPickedImages(context: Context, fallbackName: String): List<PickedImage> =
    map { uri -> PickedImage(source = uri.toString(), name = uri.displayName(context, fallbackName)) }

private fun Uri.displayName(context: Context, fallbackName: String): String {
    val fromProvider = runCatching {
        context.contentResolver
            .query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
    return fromProvider
        ?: lastPathSegment?.substringAfterLast('/')
        ?: fallbackName
}
