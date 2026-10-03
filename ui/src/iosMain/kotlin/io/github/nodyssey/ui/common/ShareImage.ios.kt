package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.toNSData
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.popoverPresentationController

/**
 * `UIActivityViewController` with a `UIImage`, which is what lets 存储图像 and the messaging apps
 * offer themselves — a file URL would be offered to the file-shaped targets instead.
 *
 * Encoded through Skia, which is what Compose draws with on this platform, so the bitmap in hand is
 * already a Skia one and nothing is copied on the way to the PNG.
 */
@Composable
actual fun rememberShareImage(dispatchers: AppDispatchers): suspend (image: ImageBitmap, chooserTitle: String?) -> Boolean =
    remember(dispatchers) {
        { image, _ ->
            runCatchingExceptCancellation {
                val png =
                    withContext(dispatchers.default) {
                        Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
                    } ?: error("PNG encode failed")
                val picture = UIImage(data = png.toNSData())
                val host = topmostViewController() ?: error("No controller to present from")
                val sheet = UIActivityViewController(activityItems = listOf(picture), applicationActivities = null)
                // The iPad presentation is a popover and needs an anchor; see `rememberShareText`.
                sheet.popoverPresentationController?.sourceView = host.view
                host.presentViewController(sheet, animated = true, completion = null)
            }.isSuccess
        }
    }
