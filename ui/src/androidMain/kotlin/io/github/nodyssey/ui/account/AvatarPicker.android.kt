package io.github.nodyssey.ui.account

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import io.github.nodyssey.data.account.AvatarUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Wires the photo picker and the camera to [onPicked].
 *
 * `PickVisualMedia` rather than `GetContent`: it is the system picker, so it needs no storage
 * permission and shows nothing but the images the user selects. The camera path uses
 * `TakePicturePreview`, which likewise needs no permission and no `FileProvider` — the trade is that
 * it returns a thumbnail-resolution bitmap. That is fine for an avatar the site renders at 100px, but
 * whoever wires the real upload should check the accepted dimensions and move to `TakePicture` with a
 * cache-file Uri if they turn out to be larger.
 */
@Composable
internal actual fun rememberAvatarPicker(
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
): AvatarPickerController {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val currentPicked = rememberUpdatedState(onPicked)
    val currentFailed = rememberUpdatedState(onFailed)

    /**
     * Both launchers end here, and [prepare] runs off the main thread — the same shape as the iOS
     * actual, and for the same reason it gives: the launcher callbacks arrive on the main thread,
     * and what stands behind them is a decode of a camera-roll photo plus a JPEG encode. Done
     * inline that is a visible hitch, and on a cheap device an ANR candidate.
     */
    fun accept(prepare: () -> PendingAvatar?) {
        scope.launch {
            val avatar = withContext(Dispatchers.Default) { prepare() }
            if (avatar == null) currentFailed.value() else currentPicked.value(avatar)
        }
    }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            // A null bitmap is the user backing out of the camera, which is not a failure.
            if (bitmap == null) return@rememberLauncherForActivityResult
            accept { runCatching { bitmap.toPendingAvatar() }.getOrNull() }
        }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            accept { resolver.decodeAvatar(uri) }
        }

    return remember(cameraLauncher, galleryLauncher) {
        AvatarPickerController(
            takePhoto = { cameraLauncher.launch(null) },
            pickImage = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
    }
}

/**
 * Decodes at a bounded size rather than loading the original.
 *
 * A modern camera roll photo is several thousand pixels on a side; decoding one whole so it can be
 * scaled down to 512 is how a settings screen gets an OutOfMemoryError on a cheap device. The
 * two-pass `inJustDecodeBounds` read costs one header parse and removes that entirely.
 */
private fun ContentResolver.decodeAvatar(uri: Uri): PendingAvatar? =
    runCatching {
        val bounds =
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }.first { longestEdge / it <= AVATAR_MAX_EDGE_PX }
            }
        val decoded = openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        decoded?.toPendingAvatar()
    }.getOrNull()

private fun Bitmap.toPendingAvatar(): PendingAvatar {
    val scaled = scaledToAvatar()
    val bytes =
        ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, stream)
            stream.toByteArray()
        }
    return PendingAvatar(
        preview = scaled.asImageBitmap(),
        upload = AvatarUpload(bytes = bytes, mimeType = AVATAR_MIME_TYPE),
    )
}

private fun Bitmap.scaledToAvatar(): Bitmap {
    val longestEdge = max(width, height)
    if (longestEdge <= AVATAR_MAX_EDGE_PX) return this
    val ratio = AVATAR_MAX_EDGE_PX.toFloat() / longestEdge
    return scale(
        width = (width * ratio).toInt().coerceAtLeast(1),
        height = (height * ratio).toInt().coerceAtLeast(1),
    )
}
