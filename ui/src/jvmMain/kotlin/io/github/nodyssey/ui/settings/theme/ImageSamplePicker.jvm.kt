package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * No picker here yet.
 *
 * A desktop file chooser is a real answer to this and not a hard one, but writing it now would be
 * writing a feature for a build nobody ships. The eyedropper button is the only thing that reaches
 * this, and the sheet's colour panel is what it falls back to.
 */
@Composable
actual fun rememberImageSamplePicker(
    targetSizePx: Int,
    onPicking: () -> Unit,
    onPicked: (ImageBitmap?) -> Unit,
): () -> Unit = {}
