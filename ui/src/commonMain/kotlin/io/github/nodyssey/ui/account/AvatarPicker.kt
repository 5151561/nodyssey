package io.github.nodyssey.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.nodyssey.data.account.AvatarUpload

/**
 * An avatar the user has chosen but the server has not accepted yet.
 *
 * [preview] is a Compose image rather than a Uri because the two sources disagree about what they
 * hand back — the photo picker returns a `content://` Uri and the camera returns a Bitmap with no
 * Uri at all — and normalising towards the Uri would mean writing the camera shot to a cache file
 * whose lifetime nobody owns. Normalising towards the bitmap costs about a megabyte of state and
 * nothing else.
 */
data class PendingAvatar(
    val preview: ImageBitmap,
    val upload: AvatarUpload,
)

/** The two avatar sources 个人信息 offers, handed to the screen as plain callbacks. */
internal class AvatarPickerController(
    val takePhoto: () -> Unit,
    val pickImage: () -> Unit,
)

/**
 * Wires whatever image sources this platform has to [onPicked].
 *
 * `expect` because both of Android's are: the system photo picker needs no storage permission and
 * `TakePicturePreview` needs no camera permission, and neither has a neutral shape. A platform with
 * only one of the two answers with a controller whose other lambda does nothing — the screen shows
 * both buttons either way, which is the same trade `AboutAppScreen` makes for the install permission.
 *
 * [onFailed] is a picture that could not be decoded, which the screen words; a user backing out of
 * the picker is neither callback.
 */
@Composable
internal expect fun rememberAvatarPicker(
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
): AvatarPickerController

/** The site renders avatars small; 512 leaves room for a retina display and nothing is gained above it. */
internal const val AVATAR_MAX_EDGE_PX = 512
internal const val AVATAR_JPEG_QUALITY = 88
internal const val AVATAR_MIME_TYPE = "image/jpeg"
