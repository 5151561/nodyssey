package io.github.nodyssey.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Decodes to a Compose image, or null when the bytes are not a picture this platform reads.
 *
 * `decodeToImageBitmap` is Compose Resources' own decoder — what `painterResource` uses — so this
 * stays inside the Skia that Compose already has, rather than opening a second bridge from `UIImage`
 * through `CGImage` to `ImageBitmap`. It throws on malformed input, and "this picture could not be
 * decoded" is an outcome every caller here already words, so it is caught rather than propagated.
 */
internal fun ByteArray.toImageBitmapOrNull(): ImageBitmap? =
    runCatching { decodeToImageBitmap() }.getOrNull()
