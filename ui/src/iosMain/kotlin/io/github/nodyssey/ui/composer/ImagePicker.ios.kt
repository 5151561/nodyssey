package io.github.nodyssey.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.nodyssey.data.composer.PickedImage

/**
 * Not wired up yet, and the reason is the work behind it rather than a missing API.
 *
 * `PHPickerViewController` is iOS's answer and it is presentable from here — `rememberShareText` finds
 * a host controller the same way. What it hands back is an `NSItemProvider` per pick, and turning that
 * into a [PickedImage] means loading each one, writing it somewhere the image loader can read back,
 * and owning the lifetime of what was written. That is a screenful of image plumbing per picker, and
 * nothing on this platform can run a line of it: there is no iOS shell, so there is no screen to open
 * a picker from and no upload for it to feed.
 *
 * A no-op rather than a `TODO()`: a lambda that throws would be a crash waiting for the first shell to
 * reach it, and this is a button that has nothing behind it, which is a different thing to be.
 */
@Composable
actual fun rememberImagePicker(
    maxItems: Int,
    fallbackName: String,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit = remember { {} }
