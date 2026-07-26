package io.github.nsreader.ui.viewer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.runCatchingExceptCancellation
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Why a save did not happen, so the screen can say something more useful than "失败". */
enum class SaveOutcome {
    SAVED,
    UNSUPPORTED_OS,
    FAILED,
}

/**
 * Writes a viewed image into the shared image collection.
 *
 * The bytes come from Coil rather than from a fresh request: the image is already on screen, and
 * re-fetching it would need the same cookies and browser headers that only the app's OkHttp client
 * carries — which is exactly what Coil is configured with. The disk cache's original encoded bytes
 * are preferred over the decoded bitmap: copying them keeps the source format and file size, where
 * re-encoding a large photo as PNG costs seconds of CPU and a file several times bigger. The bitmap
 * path survives only as the fallback for an image the cache cannot hand back.
 *
 * Scoped storage only. On Android 10 and above `RELATIVE_PATH` puts the file in `Pictures/NodeSeek`
 * with no permission at all; below that the same write needs `WRITE_EXTERNAL_STORAGE`, a broad,
 * device-wide grant to add one screenshot to the gallery. That trade is not worth making, so older
 * versions are told to use 分享 instead.
 */
suspend fun saveImageToGallery(
    context: Context,
    url: String,
    dispatchers: AppDispatchers,
): SaveOutcome {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return SaveOutcome.UNSUPPORTED_OS

    return withContext(dispatchers.io) {
        runCatchingExceptCancellation {
            val bytes = encodedBytes(context, url)
            val mimeType = bytes?.let(::sniffImageMime)
            if (bytes != null && mimeType != null) {
                writeToGallery(context, url.toFileName(mimeType.extension), mimeType.value) { stream ->
                    stream.write(bytes)
                    true
                }
            } else {
                val request = ImageRequest.Builder(context).data(url).build()
                val result = SingletonImageLoader.get(context).execute(request)
                val bitmap =
                    (result as? SuccessResult)?.image?.toBitmap()
                        ?: return@runCatchingExceptCancellation false
                writeToGallery(context, url.toFileName("png"), "image/png") { stream ->
                    // PNG is lossless; the mandatory quality argument is ignored by this format.
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }.fold(
            onSuccess = { saved -> if (saved) SaveOutcome.SAVED else SaveOutcome.FAILED },
            onFailure = { SaveOutcome.FAILED },
        )
    }
}

/**
 * The image's original encoded bytes from Coil's disk cache, populated by a request when cold.
 *
 * Null when the cache cannot produce them (cache disabled, or the request was served from memory
 * without ever touching disk) — the caller then falls back to re-encoding the decoded bitmap.
 */
private suspend fun encodedBytes(context: Context, url: String): ByteArray? {
    val loader = SingletonImageLoader.get(context)
    val cache = loader.diskCache ?: return null

    fun fromCache(): ByteArray? =
        cache.openSnapshot(url)?.use { snapshot ->
            cache.fileSystem.read(snapshot.data) { readByteArray() }
        }

    fromCache()?.let { return it }
    val result = loader.execute(ImageRequest.Builder(context).data(url).build())
    if (result !is SuccessResult) return null
    return fromCache()
}

/**
 * Inserts a pending row, hands [write] the stream, and either publishes or deletes the row.
 * A row without bytes is a zero-length ghost in the gallery, so failure takes it back out.
 */
private fun writeToGallery(
    context: Context,
    fileName: String,
    mimeType: String,
    write: (OutputStream) -> Boolean,
): Boolean {
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NodeSeek")
            // Hidden from the gallery until the bytes are actually there.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    var written = false
    try {
        resolver.openOutputStream(uri)?.use { stream -> written = write(stream) }
    } finally {
        if (written) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
    }
    return written
}

internal class ImageMime(val value: String, val extension: String)

/** Format from the magic bytes: the URL's extension lies often enough not to be trusted. */
internal fun sniffImageMime(bytes: ByteArray): ImageMime? {
    fun ascii(from: Int, until: Int): String? =
        if (bytes.size >= until) String(bytes, from, until - from, Charsets.US_ASCII) else null
    return when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() ->
            ImageMime("image/jpeg", "jpg")

        bytes.size >= 4 && bytes[0] == 0x89.toByte() && ascii(1, 4) == "PNG" ->
            ImageMime("image/png", "png")

        ascii(0, 6) == "GIF87a" || ascii(0, 6) == "GIF89a" -> ImageMime("image/gif", "gif")

        ascii(0, 4) == "RIFF" && ascii(8, 12) == "WEBP" -> ImageMime("image/webp", "webp")

        else -> null
    }
}

/** The site's own filename when the URL has one, so the gallery entry is recognisable. */
internal fun String.toFileName(extension: String = "png"): String {
    val candidate = substringBefore('?').substringAfterLast('/').trim()
    val safe = candidate.filter { it.isLetterOrDigit() || it in "-_." }
    return when {
        safe.isEmpty() -> "nodeseek-image.$extension"
        safe.endsWith(".$extension", ignoreCase = true) -> safe
        safe.contains('.') -> safe.substringBeforeLast('.') + ".$extension"
        else -> "$safe.$extension"
    }
}
