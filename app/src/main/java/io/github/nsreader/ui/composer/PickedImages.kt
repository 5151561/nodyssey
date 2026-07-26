package io.github.nsreader.ui.composer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.nsreader.R
import io.github.nsreader.data.composer.PickedImage

/**
 * Turns what the photo picker returns into queue entries.
 *
 * The display name is worth the content-provider query: it is what the tray labels, what the
 * failure message quotes back, and what ends up as the image's alt text in the published Markdown.
 * Providers are allowed to answer with nothing, hence the two fallbacks.
 */
internal fun List<Uri>.toPickedImages(context: Context): List<PickedImage> =
    map { uri -> PickedImage(source = uri.toString(), name = uri.displayName(context)) }

private fun Uri.displayName(context: Context): String {
    val fromProvider = runCatching {
        context.contentResolver
            .query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
    return fromProvider
        ?: lastPathSegment?.substringAfterLast('/')
        ?: context.getString(R.string.composer_image_default_name)
}
