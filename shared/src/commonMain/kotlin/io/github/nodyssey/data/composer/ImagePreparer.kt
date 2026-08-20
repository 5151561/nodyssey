package io.github.nodyssey.data.composer

import io.github.nodyssey.data.imagehost.ImageHostUpload

/**
 * Turns whatever the photo picker handed back into bytes an image host will take.
 *
 * [source] is a string rather than a `Uri` because what the picker hands back is the platform's
 * business: the implementation that reads a `content://` URI lives in `platform/`, and everything
 * from here to the upload only needs bytes, a name and a type.
 */
fun interface ImagePreparer {
    suspend fun prepare(source: String, displayName: String): ImageHostUpload
}

/** `IMG_0421.HEIC` → `IMG_0421.webp`; a name with no extension just gains one. */
fun String.withExtension(extension: String): String {
    val base = substringBeforeLast('.', missingDelimiterValue = this).ifBlank { "image" }
    return "$base.$extension"
}
