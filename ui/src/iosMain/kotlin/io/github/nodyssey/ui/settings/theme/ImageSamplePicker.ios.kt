package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Not wired up yet, for the reason [io.github.nodyssey.ui.composer.rememberImagePicker] gives, plus
 * one of its own: the decode.
 *
 * This seam's `expect` says both halves are the platform's, and on iOS the second half is the harder
 * one — a `UIImage` is not an [ImageBitmap], and bridging them goes through Skia rather than through
 * anything Foundation offers. Neither half is guesswork; both are code with nothing to run it.
 *
 * The sheet handles this correctly as it stands: [onPicking] never fires, so it never enters the state
 * that waits for a decode.
 */
@Composable
actual fun rememberImageSamplePicker(
    targetSizePx: Int,
    onPicking: () -> Unit,
    onPicked: (ImageBitmap?) -> Unit,
): () -> Unit = remember { {} }
