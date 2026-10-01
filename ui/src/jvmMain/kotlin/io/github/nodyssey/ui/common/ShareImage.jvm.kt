package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.plaza.core.AppDispatchers

/** No share sheet on a desktop, for the reason [rememberShareText] gives. */
@Composable
actual fun rememberShareImage(dispatchers: AppDispatchers): suspend (image: ImageBitmap, chooserTitle: String?) -> Boolean =
    { _, _ -> false }
