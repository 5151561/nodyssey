package io.github.nodyssey.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.plaza.core.AppDispatchers

/**
 * A saver that always declines, and the outcome it declines with is the closest one that exists.
 *
 * iOS *can* write to the photo library — `PHPhotoLibrary` plus its own add-only permission — so
 * [SaveOutcome.UNSUPPORTED_OS] overstates the case slightly: what is unsupported is this build, not
 * the platform. It is still the right one of the three to return, because it is the one the screen
 * words as "this cannot be done here" rather than as a failure worth retrying, and a retry is exactly
 * what would not help.
 *
 * What it takes to do properly: fetch the bytes through the app's own transport rather than a second
 * HTTP client, sniff the format with [sniffImageMime] — that half is already common — ask for the
 * add-only authorisation, and write through `PHAssetCreationRequest`. The step that gives iOS a shell
 * is the one that can run it.
 */
@Composable
actual fun rememberImageGallerySaver(dispatchers: AppDispatchers): ImageGallerySaver =
    remember {
        object : ImageGallerySaver {
            override suspend fun save(url: String): SaveOutcome = SaveOutcome.UNSUPPORTED_OS
        }
    }
