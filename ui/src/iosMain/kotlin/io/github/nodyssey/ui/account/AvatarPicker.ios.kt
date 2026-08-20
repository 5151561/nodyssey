package io.github.nodyssey.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.nodyssey.data.account.AvatarUpload
import io.github.nodyssey.ui.common.loadImageData
import io.github.nodyssey.ui.common.rememberPhotoPicker
import io.github.nodyssey.ui.common.toImageBitmapOrNull
import io.github.nodyssey.ui.common.topmostViewController
import io.github.plaza.core.image.downscaledJpeg
import io.github.plaza.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * The two sources 个人信息 offers, wired to the two APIs iOS has for them.
 *
 * The camera is `UIImagePickerController` rather than anything newer: `PHPickerViewController` reads
 * the library and has no camera mode, and the alternative — a full `AVCaptureSession` — is a camera
 * *app*, not a way to take one square picture. The gallery half is the shared photo picker, so only
 * this half costs a usage description (`NSCameraUsageDescription`) in the shell's `Info.plist`.
 *
 * On a simulator there is no camera at all, and `isSourceTypeAvailable` says so — the button is then
 * the no-op the `expect` describes, which is also what a device with the camera restricted gets.
 */
@Composable
internal actual fun rememberAvatarPicker(
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
): AvatarPickerController {
    val scope = rememberCoroutineScope()
    val currentPicked = rememberUpdatedState(onPicked)
    val currentFailed = rememberUpdatedState(onFailed)

    /** Both sources end here: bytes in, a downscaled JPEG and its preview out. */
    fun accept(data: NSData?) {
        if (data == null) {
            currentFailed.value()
            return
        }
        scope.launch {
            val avatar = withContext(Dispatchers.Default) { data.toPendingAvatar() }
            if (avatar == null) currentFailed.value() else currentPicked.value(avatar)
        }
    }

    val pickImage = rememberPhotoPicker(selectionLimit = 1) { providers ->
        scope.launch { accept(providers.first().loadImageData()) }
    }

    // Held across recomposition for the reason the photo picker's delegate is: the controller keeps
    // only a weak reference to it, and a delegate collected while the camera is open is a camera that
    // hands its picture to nothing.
    val cameraDelegate = remember { CameraDelegate(::accept) }

    return remember(pickImage, cameraDelegate) {
        AvatarPickerController(
            takePhoto = {
                if (UIImagePickerController.isSourceTypeAvailable(
                        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
                    )
                ) {
                    val camera =
                        UIImagePickerController().apply {
                            sourceType =
                                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                            delegate = cameraDelegate
                        }
                    topmostViewController()
                        ?.presentViewController(camera, animated = true, completion = null)
                }
            },
            pickImage = pickImage,
        )
    }
}

/**
 * The camera's half of the wiring.
 *
 * The picture arrives as a decoded `UIImage`, and the pipeline below takes bytes — so it is encoded
 * once at full quality on the way in and re-encoded at [AVATAR_JPEG_QUALITY] on the way out. That
 * round trip buys one downscale path for both sources instead of two, and it costs a JPEG of an image
 * that is already in memory, once, at the moment the shutter closes.
 */
private class CameraDelegate(
    private val onImage: (NSData?) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        onImage(image?.let { UIImageJPEGRepresentation(it, 1.0) })
    }

    /** Backing out of the camera is neither callback — the same as Android's null bitmap. */
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

/**
 * Decodes at a bounded size and re-encodes, or null when the bytes are not a picture.
 *
 * Bounded rather than whole for the reason `AvatarPicker.android.kt` gives about `inSampleSize`: a
 * camera roll photo is several thousand pixels on a side, and decoding one in full so it can be
 * scaled down to 512 is how a settings screen runs a phone out of memory.
 */
private fun NSData.toPendingAvatar(): PendingAvatar? {
    val jpeg = downscaledJpeg(AVATAR_MAX_EDGE_PX, AVATAR_JPEG_QUALITY / 100.0) ?: return null
    val bytes = jpeg.toByteArray()
    val preview = bytes.toImageBitmapOrNull() ?: return null
    return PendingAvatar(
        preview = preview,
        upload = AvatarUpload(bytes = bytes, mimeType = AVATAR_MIME_TYPE),
    )
}
