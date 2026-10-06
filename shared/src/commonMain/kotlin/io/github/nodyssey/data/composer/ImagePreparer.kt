package io.github.nodyssey.data.composer

import io.github.nodyssey.data.imagehost.ImageHostUpload

/**
 * Turns whatever the photo picker handed back into bytes an image host will take.
 *
 * [source] is a string rather than a `Uri` because what the picker hands back is the platform's
 * business: the implementation that reads a `content://` URI lives in `platform/`, and everything
 * from here to the upload only needs bytes, a name and a type.
 */
interface ImagePreparer {
    suspend fun prepare(source: String, displayName: String): ImageHostUpload

    /**
     * The picked file's own bytes, untouched — 表情's 保留原图. [prepare] re-encodes a still to WebP
     * (JPEG on iOS) and only spares a GIF, so an animated WebP would go out as its first frame; a
     * sticker is a few kilobytes and is worth sending as it is. A file that is not one of the
     * formats a browser draws — HEIC above all — goes through [prepare] after all; see
     * [originalUpload].
     */
    suspend fun original(source: String, displayName: String): ImageHostUpload
}

/**
 * [bytes] read as whatever their magic number says, with [displayName] given the matching
 * extension — or null when they are none of GIF, PNG, JPEG and WebP, for the caller to re-encode
 * through [ImagePreparer.prepare] instead.
 *
 * Null rather than the bytes as an octet stream because 保留原图 is on by default and the camera's
 * own format is HEIC on an iPhone and on many Android phones: kept as it is, that is a file the host
 * stores as `.HEIC` and a browser does not draw, so the sticker is broken in every post that uses
 * it. Only the four formats a web page shows are worth keeping untouched.
 */
fun originalUpload(bytes: ByteArray, displayName: String): ImageHostUpload? {
    val (mime, extension) = sniffImageType(bytes) ?: return null
    return ImageHostUpload(bytes, displayName.withExtension(extension), mime)
}

private fun sniffImageType(bytes: ByteArray): Pair<String, String>? {
    fun at(index: Int) = bytes.getOrNull(index)?.toInt()?.and(0xFF) ?: -1
    return when {
        at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "image/gif" to "gif"

        at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png" to "png"

        at(0) == 0xFF && at(1) == 0xD8 -> "image/jpeg" to "jpg"

        at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
            at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "image/webp" to "webp"

        else -> null
    }
}

/** `IMG_0421.HEIC` → `IMG_0421.webp`; a name with no extension just gains one. */
fun String.withExtension(extension: String): String {
    val base = substringBeforeLast('.', missingDelimiterValue = this).ifBlank { "image" }
    return "$base.$extension"
}
