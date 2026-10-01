package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.plaza.core.AppDispatchers

/**
 * Hands a picture to whatever this platform means by 分享 — [rememberShareText]'s sibling for bytes.
 *
 * Suspending because the picture has to be encoded before anything can receive it, and a floor drawn
 * at a phone's density is a few megapixels of PNG: that is work for [AppDispatchers.default], not
 * the frame. Answers whether the sheet was put up, so the caller can say so when it was not.
 *
 * [chooserTitle] is what Android's chooser is labelled with, as for [rememberShareText].
 */
@Composable
expect fun rememberShareImage(dispatchers: AppDispatchers): suspend (image: ImageBitmap, chooserTitle: String?) -> Boolean
