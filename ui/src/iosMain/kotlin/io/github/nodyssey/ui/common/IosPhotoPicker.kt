package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * The system photo picker, as the three seams above it need it.
 *
 * `PHPickerViewController` rather than `UIImagePickerController`: it runs out of process, so it needs
 * no photo library permission at all and the app never sees a picture the user did not choose. That
 * is the same trade `PickVisualMedia` makes on Android, which is why the two sides of each `expect`
 * end up with matching privacy stories rather than one of them asking for a device-wide grant.
 *
 * What comes back is an [NSItemProvider] per pick — a promise of bytes, not the bytes — so every
 * caller finishes the job with [loadImageData].
 *
 * @param selectionLimit how many pictures may be chosen; 1 for the single-image seams.
 */
@Composable
internal fun rememberPhotoPicker(
    selectionLimit: Int,
    onPicked: (List<NSItemProvider>) -> Unit,
): () -> Unit {
    // The delegate is held weakly by the picker, so composition is what keeps it alive — which is
    // also what the `expect` means by "registered while the screen is alive". `rememberUpdatedState`
    // rather than keying the delegate on the callback: a recomposition with a new lambda must not
    // replace the delegate of a picker that is already on screen.
    val current = rememberUpdatedState(onPicked)
    val delegate = remember { PhotoPickerDelegate { providers -> current.value(providers) } }

    return remember(selectionLimit, delegate) {
        {
            val configuration =
                PHPickerConfiguration().apply {
                    this.selectionLimit = selectionLimit.toLong()
                    // Stills only: the tray, the avatar and the palette sampler all decode what they
                    // are handed, and a video would arrive as bytes none of them can read.
                    filter = PHPickerFilter.imagesFilter()
                }
            val picker = PHPickerViewController(configuration = configuration)
            picker.delegate = delegate
            topmostViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PhotoPickerDelegate(
    private val onPicked: (List<NSItemProvider>) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    /**
     * Called for a finished pick *and* for a cancelled one, the latter with an empty list — unlike
     * Android's launcher, which reports the same thing as a null result. Dismissal is the delegate's
     * job either way: `PHPickerViewController` does not take itself down.
     */
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val providers = didFinishPicking.filterIsInstance<PHPickerResult>().map { it.itemProvider }
        if (providers.isNotEmpty()) onPicked(providers)
    }
}

/**
 * The bytes behind one pick, or null when the provider cannot produce an image.
 *
 * `public.image` rather than a concrete format: whatever the picture is stored as — HEIC on a modern
 * camera roll — is what arrives, and transcoding is the upload's business, not the picker's. The same
 * split Android has, where `toPickedImages` hands the content URI on untouched.
 *
 * The completion runs on a queue of the provider's choosing, which is why every caller resumes into a
 * coroutine here rather than calling back from wherever this lands.
 */
internal suspend fun NSItemProvider.loadImageData(): NSData? =
    suspendCancellableCoroutine { continuation ->
        loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
            continuation.resume(data)
        }
    }
