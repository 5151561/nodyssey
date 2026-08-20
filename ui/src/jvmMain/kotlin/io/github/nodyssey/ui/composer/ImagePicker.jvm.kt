package io.github.nodyssey.ui.composer

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.composer.PickedImage

/**
 * No picker here yet — see `ImageSamplePicker.jvm.kt` for the same note. The composer's 图片 button
 * is what reaches this, and it does nothing rather than opening something that cannot upload.
 */
@Composable
actual fun rememberImagePicker(
    maxItems: Int,
    fallbackName: String,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit = {}
