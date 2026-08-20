package io.github.nodyssey.ui.settings.theme

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * `PickVisualMedia` — the system picker, so it needs no storage permission and shows nothing but the
 * image the user selects.
 */
@Composable
actual fun rememberImageSamplePicker(
    targetSizePx: Int,
    onPicking: () -> Unit,
    onPicked: (ImageBitmap?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // A null uri is the user backing out, which is not a decode that failed.
            if (uri == null) return@rememberLauncherForActivityResult
            onPicking()
            scope.launch {
                onPicked(decodeSample(context.contentResolver, uri, targetSizePx))
            }
        }
    return {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

private suspend fun decodeSample(
    resolver: android.content.ContentResolver,
    uri: Uri,
    targetSizePx: Int,
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(
                        generateSequence(1) { it * 2 }.first { longest / it <= targetSizePx },
                    )
                    // Software, because the pixels are read back one at a time by the sampler and a
                    // hardware bitmap has none to read.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, uri)
            }
        }.getOrNull()?.asImageBitmap()
    }
