package io.github.nodyssey.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.plaza.core.AppDispatchers

/**
 * No shared picture collection to write into.
 *
 * [SaveOutcome.UNSUPPORTED_OS] rather than [SaveOutcome.FAILED]: the screen's wording for it is
 * "用分享代替", which is the right advice on Android 9 and the right advice here.
 */
@Composable
actual fun rememberImageGallerySaver(dispatchers: AppDispatchers): ImageGallerySaver =
    remember {
        object : ImageGallerySaver {
            override suspend fun save(url: String): SaveOutcome = SaveOutcome.UNSUPPORTED_OS
        }
    }
