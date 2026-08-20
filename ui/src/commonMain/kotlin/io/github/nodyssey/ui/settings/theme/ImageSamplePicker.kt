package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Opens the platform's photo picker and hands back the chosen picture, decoded.
 *
 * Three callbacks rather than one because the decode is the slow part and the sheet says so:
 * [onPicking] fires the moment a picture is chosen, [onPicked] once it is decoded — with null when
 * the decode failed, which puts the sheet back where it was.
 *
 * [targetSizePx] is the longest edge the caller has any use for; a modern phone photo is tens of
 * megapixels and the panel it lands in is a few hundred pixels wide.
 *
 * `expect` because *both* halves are the platform's: which picker needs no storage permission, and
 * how to decode at a bounded size without loading the original first.
 */
@Composable
expect fun rememberImageSamplePicker(
    targetSizePx: Int,
    onPicking: () -> Unit,
    onPicked: (ImageBitmap?) -> Unit,
): () -> Unit
