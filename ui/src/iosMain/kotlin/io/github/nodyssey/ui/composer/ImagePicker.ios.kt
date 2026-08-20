@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.ui.common.loadImageData
import io.github.nodyssey.ui.common.rememberPhotoPicker
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToURL

/**
 * `PHPickerViewController` — the out-of-process picker, so it costs no photo library permission and
 * the app only ever sees what was chosen.
 *
 * What the picker returns is a promise of bytes rather than an address, which is the one place this
 * differs in shape from Android: there is no `content://` URI to hand on, so each pick is loaded and
 * written to a file whose URL becomes the [PickedImage.source]. See [pickedImagesDirectory] for who
 * owns that file afterwards.
 */
@Composable
actual fun rememberImagePicker(
    maxItems: Int,
    fallbackName: String,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return rememberPhotoPicker(selectionLimit = maxItems) { providers ->
        scope.launch {
            // Concurrently: the loads are independent, and a pick of ten images is ten round trips
            // to another process. Order is preserved because `awaitAll` returns in argument order,
            // and the tray shows them in the order they were chosen.
            val picked =
                providers
                    .map { provider -> async { provider.toPickedImage(fallbackName) } }
                    .awaitAll()
                    .filterNotNull()
            if (picked.isNotEmpty()) onPicked(picked)
        }
    }
}

/**
 * Loads one pick and writes it where the image loader and the uploader can both read it back.
 *
 * The bytes are written untouched — HEIC stays HEIC. Transcoding belongs to the upload, the same way
 * Android hands the content URI on without opening it; what this owes the layers above is an address
 * and a name, and both are strings.
 *
 * Null when the provider could not produce image bytes or the write failed. A picture that cannot be
 * read is dropped rather than queued: the tray's failure states are about uploads, and a row that
 * could never be uploaded would sit in one of them explaining nothing.
 */
private suspend fun NSItemProvider.toPickedImage(fallbackName: String): PickedImage? {
    val data = loadImageData() ?: return null
    val directory = pickedImagesDirectory ?: return null

    // The suggested name is the camera roll's, and it is what the tray labels, what a failure quotes
    // back and what becomes the image's alt text in the published Markdown. It carries no extension,
    // so the file gets a name of its own and this one is only ever shown.
    val name = suggestedName?.takeIf { it.isNotBlank() } ?: fallbackName
    val file = directory.URLByAppendingPathComponent(NSUUID().UUIDString()) ?: return null
    if (!data.writeToURL(file, atomically = true)) return null

    return PickedImage(source = file.absoluteString ?: return null, name = name)
}

/**
 * Where picked images are written, cleared once per process.
 *
 * The temporary directory rather than the caches directory: these files exist to be read by an upload
 * that is about to happen, and iOS is allowed to reclaim the whole directory when the app is not
 * running — which is exactly the lifetime they want.
 *
 * Clearing at first use is safe because the tray is in memory only: [io.github.nodyssey.data.composer.ImageUploadQueue]
 * holds its rows in a `StateFlow` owned by the composer's `ViewModel`, so nothing written by an
 * earlier launch of the app is still referenced by anything. Without the sweep, a picked image that
 * was never uploaded would sit on disk until iOS felt like reclaiming it.
 */
private val pickedImagesDirectory: NSURL? by lazy {
    val directory =
        NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent("picked-images", isDirectory = true)
    if (directory == null) {
        null
    } else {
        val fileManager = NSFileManager.defaultManager
        fileManager.removeItemAtURL(directory, error = null)
        val created =
            fileManager.createDirectoryAtURL(
                directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        if (created) directory else null
    }
}
