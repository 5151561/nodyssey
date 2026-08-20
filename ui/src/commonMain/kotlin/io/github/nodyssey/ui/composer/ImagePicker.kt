package io.github.nodyssey.ui.composer

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.composer.PickedImage

/**
 * Opens the platform's photo picker and hands the chosen images to [onPicked] as upload queue rows.
 *
 * [PickedImage] rather than a platform handle: what an upload needs is an address the image loader
 * can read back and a name to label the tray with, and both are strings. Resolving the name is the
 * platform's job — on Android it is a content-provider query — hence [fallbackName], which the caller
 * reads from the string resources it already has in composition.
 *
 * Returns a lambda rather than being one: the picker has to be registered while the screen is alive.
 */
@Composable
expect fun rememberImagePicker(
    maxItems: Int,
    fallbackName: String,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit
