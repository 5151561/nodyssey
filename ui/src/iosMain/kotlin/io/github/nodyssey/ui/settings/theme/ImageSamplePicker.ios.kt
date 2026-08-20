package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import io.github.nodyssey.ui.common.loadImageData
import io.github.nodyssey.ui.common.rememberPhotoPicker
import io.github.nodyssey.ui.common.toImageBitmapOrNull
import io.github.plaza.core.image.downscaledPng
import io.github.plaza.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * `PHPickerViewController` again — no photo library permission, and the app sees only the one picture.
 *
 * The decode is bounded the way the Android side's `setTargetSampleSize` is: what the sheet does with
 * the result is read a handful of pixels out of it to seed a palette, and a full-resolution decode of
 * a modern photo would be forty megabytes to answer a question about three colours.
 */
@Composable
actual fun rememberImageSamplePicker(
    targetSizePx: Int,
    onPicking: () -> Unit,
    onPicked: (ImageBitmap?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return rememberPhotoPicker(selectionLimit = 1) { providers ->
        // On the main thread, and before anything is loaded: this is the callback that puts the sheet
        // into its waiting state, and the wait it is announcing starts here.
        onPicking()
        scope.launch {
            val data = providers.first().loadImageData()
            val sampled =
                data?.let {
                    withContext(Dispatchers.Default) {
                        it.downscaledPng(targetSizePx)?.toByteArray()?.toImageBitmapOrNull()
                    }
                }
            onPicked(sampled)
        }
    }
}
