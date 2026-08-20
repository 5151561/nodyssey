package io.github.nodyssey.ui.viewer

import androidx.compose.runtime.Composable
import io.github.plaza.core.AppDispatchers

/** Why a save did not happen, so the screen can say something more useful than "失败". */
enum class SaveOutcome {
    SAVED,
    UNSUPPORTED_OS,
    FAILED,
}

/**
 * Writes a viewed image into wherever this platform keeps pictures.
 *
 * An interface rather than a function because every part of doing it is the platform's: which
 * collection, what permission it costs, and whether there is one at all — [SaveOutcome.UNSUPPORTED_OS]
 * is a real answer and the screen already words it.
 */
interface ImageGallerySaver {
    suspend fun save(url: String): SaveOutcome
}

/**
 * The saver for this platform, bound to the app's dispatchers.
 *
 * A composable because the Android one needs the context, and the screen is where one is in scope —
 * the same shape `rememberImagePicker` and `rememberShareText` take, and for the same reason.
 */
@Composable
expect fun rememberImageGallerySaver(dispatchers: AppDispatchers): ImageGallerySaver

internal class ImageMime(val value: String, val extension: String)

/** Format from the magic bytes: the URL's extension lies often enough not to be trusted. */
internal fun sniffImageMime(bytes: ByteArray): ImageMime? {
    fun ascii(from: Int, until: Int): String? =
        if (bytes.size >= until) bytes.decodeToString(from, until) else null
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
