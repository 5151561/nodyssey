package io.github.nodyssey.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.toNSData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

@Composable
actual fun rememberImageGallerySaver(dispatchers: AppDispatchers): ImageGallerySaver {
    // Coil's context, which on this platform is a singleton with nothing in it — the parameter exists
    // because Android's image loader needs one. Passed rather than reached for so the saver is built
    // from the same loader the screen above it is drawing with.
    val context = LocalPlatformContext.current
    return remember(context, dispatchers) { IosImageGallerySaver(context, dispatchers) }
}

private class IosImageGallerySaver(
    private val context: PlatformContext,
    private val dispatchers: AppDispatchers,
) : ImageGallerySaver {
    /**
     * Writes the picture into the system photo library.
     *
     * The bytes come from Coil's disk cache rather than from a fresh request, for the reason
     * `ImageSaver.android.kt` gives at length: the image is already on screen, and fetching it again
     * would need the same cookies and browser headers only the app's own transport carries — which is
     * what Coil is configured with. Their original encoding is kept, so a photo stays a JPEG instead
     * of becoming a PNG several times its size.
     *
     * Two things differ from Android, both because Photos and MediaStore ask for different things:
     *
     * - **The add-only permission.** Writing one picture into the library costs a grant here, where
     *   scoped storage costs none. Refusing it is [SaveOutcome.PERMISSION_DENIED] and not a failure —
     *   retrying is exactly what will not help.
     * - **No bitmap fallback.** MediaStore has to be told the type before the bytes exist, which is
     *   why the Android path re-encodes a decoded bitmap as PNG when it cannot get the originals.
     *   `PHAssetCreationRequest` reads the type out of the data it is handed, so there is nothing that
     *   path would buy: without the encoded bytes there is no save either way.
     */
    override suspend fun save(url: String): SaveOutcome =
        withContext(dispatchers.io) {
            runCatchingExceptCancellation {
                val bytes = encodedBytes(context, url) ?: return@runCatchingExceptCancellation SaveOutcome.FAILED
                if (!requestAddOnlyAccess()) {
                    return@runCatchingExceptCancellation SaveOutcome.PERMISSION_DENIED
                }
                // The site's own filename, when the magic bytes say what it should end in. Cosmetic —
                // Photos reads the format out of the data — so an unrecognised one is simply left for
                // the library to name.
                val fileName = sniffImageMime(bytes)?.let { url.toFileName(it.extension) }
                if (addToLibrary(bytes.toNSData(), fileName)) SaveOutcome.SAVED else SaveOutcome.FAILED
            }.getOrElse { SaveOutcome.FAILED }
        }
}

/**
 * The image's original encoded bytes from Coil's disk cache, populated by a request when cold.
 *
 * Null when the cache cannot produce them — disabled, or the request was served out of memory without
 * ever touching disk.
 */
private suspend fun encodedBytes(context: PlatformContext, url: String): ByteArray? {
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
 * Asks for add-only access, and answers whether the app has it.
 *
 * `PHAccessLevelAddOnly` rather than read-write: this app never reads the library — the pickers run
 * out of process precisely so that they do not — and the two are separate grants with separate
 * prompts. Add-only is also the one whose refusal is cheap to word, because nothing else in the app
 * depends on it.
 *
 * Already-granted goes through the same call: the system resolves it without a prompt.
 */
private suspend fun requestAddOnlyAccess(): Boolean =
    suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(
            PHAccessLevelAddOnly,
        ) { status ->
            continuation.resume(
                status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited,
            )
        }
    }

/**
 * One asset, created from the bytes as they are.
 *
 * `performChanges` is Photos' transaction: the block records the edit and the library applies it,
 * so the completion is the only place that knows whether it happened.
 */
private suspend fun addToLibrary(data: NSData, fileName: String?): Boolean =
    suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            changeBlock = {
                val options = PHAssetResourceCreationOptions()
                if (fileName != null) options.setOriginalFilename(fileName)
                PHAssetCreationRequest.creationRequestForAsset()
                    .addResourceWithType(PHAssetResourceTypePhoto, data, options)
            },
            completionHandler = { saved, _ -> continuation.resume(saved) },
        )
    }
