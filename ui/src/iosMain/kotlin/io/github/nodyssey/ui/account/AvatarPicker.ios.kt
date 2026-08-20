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

    /**
     * Both sources end here, and each hands over the shape it has rather than a common one — the
     * gallery's bytes, the camera's decoded image. [prepare] is called off the main thread, which is
     * the whole reason it is a lambda: the camera's delegate runs on the main thread and the work
     * behind it is a downscale and a JPEG encode.
     */
    fun accept(prepare: () -> PendingAvatar?) {
        scope.launch {
            val avatar = withContext(Dispatchers.Default) { prepare() }
            if (avatar == null) currentFailed.value() else currentPicked.value(avatar)
        }
    }

    val pickImage = rememberPhotoPicker(selectionLimit = 1) { providers ->
        scope.launch {
            val data = providers.first().loadImageData()
            accept { data?.toPendingAvatar() }
        }
    }

    // Held across recomposition for the reason the photo picker's delegate is: the controller keeps
    // only a weak reference to it, and a delegate collected while the camera is open is a camera that
    // hands its picture to nothing.
    val cameraDelegate = remember { CameraDelegate { image -> accept { image?.toPendingAvatar() } } }

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
 * The picture is handed on as the `UIImage` it arrives as. An earlier version turned it into bytes
 * here — `UIImageJPEGRepresentation(image, 1.0)` — so that both sources could share one downscale
 * path, and that line was three bad things at once: a full-resolution encode, at the slowest quality
 * JPEG has, on the main thread, producing a copy that the next step decoded again and threw away. The
 * shared path is now shared one step later instead, at [PendingAvatar].
 */
private class CameraDelegate(
    private val onImage: (UIImage?) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onImage(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
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
private fun NSData.toPendingAvatar(): PendingAvatar? =
    pendingAvatarOf(downscaledJpeg(AVATAR_MAX_EDGE_PX, AVATAR_JPEG_QUALITY / 100.0))

/** The camera's, from an image that is already decoded — the same bound, without the round trip. */
private fun UIImage.toPendingAvatar(): PendingAvatar? =
    pendingAvatarOf(downscaledJpeg(AVATAR_MAX_EDGE_PX, AVATAR_JPEG_QUALITY / 100.0))

/** The tail both share: the preview is decoded from the *encoded* bytes, so it is what will upload. */
private fun pendingAvatarOf(jpeg: NSData?): PendingAvatar? {
    val bytes = (jpeg ?: return null).toByteArray()
    val preview = bytes.toImageBitmapOrNull() ?: return null
    return PendingAvatar(
        preview = preview,
        upload = AvatarUpload(bytes = bytes, mimeType = AVATAR_MIME_TYPE),
    )
}
