package io.github.nodyssey.data.composer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import androidx.core.net.toUri
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.data.nodeimage.NodeImageError
import io.github.nodyssey.data.nodeimage.NodeImageException
import io.github.nodyssey.data.nodeimage.NodeImageUpload
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Turns whatever the photo picker handed back into bytes an image host will take. */
fun interface ImagePreparer {
    suspend fun prepare(source: String, displayName: String): NodeImageUpload
}

/**
 * Decodes a `content://` URI at a bounded size and re-encodes it as WebP.
 *
 * The two-pass `inJustDecodeBounds` read is what keeps a settings-screen-sized heap from meeting a
 * 50-megapixel photo: the second pass subsamples during decode, so the full-resolution bitmap never
 * exists. WebP rather than JPEG because the host converts to it anyway (its own uploader renames
 * every file to `.webp`), so encoding twice would only cost quality.
 *
 * Animated GIFs are the deliberate exception and pass through untouched — decoding one to a Bitmap
 * would silently upload a still frame, which is worse than uploading a large file.
 */
class DefaultImagePreparer(
    context: Context,
    private val dispatchers: AppDispatchers,
) : ImagePreparer {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun prepare(source: String, displayName: String): NodeImageUpload =
        withContext(dispatchers.io) {
            val uri = runCatching { source.toUri() }.getOrNull()
                ?: throw NodeImageException(NodeImageError.Unparsable, detail = displayName)
            val mimeType = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()

            if (mimeType == MIME_GIF) {
                val bytes = readAll(uri, displayName)
                return@withContext NodeImageUpload(bytes, displayName.withExtension("gif"), MIME_GIF)
            }

            val bitmap = decodeBounded(uri)
                ?: return@withContext readAll(uri, displayName).let { bytes ->
                    // Undecodable but readable: hand the original over and let the host judge it.
                    NodeImageUpload(bytes, displayName, mimeType.ifBlank { MIME_OCTET })
                }

            NodeImageUpload(
                bytes = bitmap.encodeWebp(),
                fileName = displayName.withExtension("webp"),
                mimeType = MIME_WEBP,
            )
        }

    private fun readAll(uri: Uri, displayName: String): ByteArray =
        runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
            ?: throw NodeImageException(NodeImageError.Unparsable, detail = displayName)

    private fun decodeBounded(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first { longestEdge / it <= MAX_EDGE_PX }
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        decoded?.scaledToBound()
    }.getOrNull()

    private fun Bitmap.scaledToBound(): Bitmap {
        val longestEdge = max(width, height)
        if (longestEdge <= MAX_EDGE_PX) return this
        val ratio = MAX_EDGE_PX.toFloat() / longestEdge
        return scale(
            width = (width * ratio).toInt().coerceAtLeast(1),
            height = (height * ratio).toInt().coerceAtLeast(1),
        )
    }

    private fun Bitmap.encodeWebp(): ByteArray =
        ByteArrayOutputStream().use { stream ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            compress(format, WEBP_QUALITY, stream)
            stream.toByteArray()
        }

    private companion object {
        /**
         * Forum images are read on a phone and, at most, on a laptop. 2048 covers a full-width image
         * on a retina display; beyond that the extra bytes buy nothing anyone will see.
         */
        const val MAX_EDGE_PX = 2048
        const val WEBP_QUALITY = 86
        const val MIME_WEBP = "image/webp"
        const val MIME_GIF = "image/gif"
        const val MIME_OCTET = "application/octet-stream"
    }
}

/** `IMG_0421.HEIC` → `IMG_0421.webp`; a name with no extension just gains one. */
internal fun String.withExtension(extension: String): String {
    val base = substringBeforeLast('.', missingDelimiterValue = this).ifBlank { "image" }
    return "$base.$extension"
}
