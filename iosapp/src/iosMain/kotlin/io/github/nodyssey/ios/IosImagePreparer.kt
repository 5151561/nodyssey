package io.github.nodyssey.ios

import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.composer.withExtension
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.image.downscaledJpeg
import io.github.plaza.core.toByteArray
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL

/**
 * Reads back what the picker wrote, at a bounded size, ready to upload.
 *
 * The source is a `file://` URL rather than Android's `content://` URI — see `ImagePicker.ios.kt` for
 * why the picker has to write a file here at all — and the rest is the same shape
 * `DefaultImagePreparer` has: bounded decode so a fifty-megapixel photo never exists in full, GIFs
 * passed through untouched because decoding one would silently upload a still frame, and anything
 * undecodable handed over as it is for the host to judge.
 *
 * **JPEG where Android sends WebP**, and that is the one real difference. Android picks WebP because
 * it is the smallest format all six hosts accept and nodeimage.com re-encodes to it regardless.
 * ImageIO decodes WebP on this platform but its *encoder* is not something to rely on across the
 * versions this app supports, and a format that might not exist at runtime is a worse trade than one
 * that is a little larger. All six hosts take JPEG.
 */
class IosImagePreparer(
    private val dispatchers: AppDispatchers = AppDispatchers(),
) : ImagePreparer {
    override suspend fun prepare(source: String, displayName: String): ImageHostUpload =
        withContext(dispatchers.io) {
            val url =
                NSURL.URLWithString(source)
                    ?: throw ImageHostException(ImageHostError.Unparsable, detail = displayName)
            val data: NSData =
                NSData.dataWithContentsOfURL(url)
                    ?: throw ImageHostException(ImageHostError.Unparsable, detail = displayName)

            val bytes = data.toByteArray()
            if (bytes.isGif()) {
                return@withContext ImageHostUpload(bytes, displayName.withExtension("gif"), MIME_GIF)
            }

            val bounded = data.downscaledJpeg(MAX_EDGE_PX, JPEG_QUALITY / 100.0)
            if (bounded == null) {
                // Readable but not decodable: hand the original over and let the host judge it.
                ImageHostUpload(bytes, displayName, MIME_OCTET)
            } else {
                ImageHostUpload(bounded.toByteArray(), displayName.withExtension("jpg"), MIME_JPEG)
            }
        }

    /**
     * The magic bytes rather than the file name.
     *
     * The name came from the camera roll and the extension came from whatever the picker suggested;
     * neither is a statement about the contents, and an animation uploaded as a still is the one
     * failure here nobody notices until it is published.
     */
    private fun ByteArray.isGif(): Boolean =
        size >= 6 && decodeToString(0, 6).let { it == "GIF87a" || it == "GIF89a" }

    private companion object {
        /**
         * Forum images are read on a phone and, at most, on a laptop. 2048 covers a full-width image
         * on a retina display; beyond that the extra bytes buy nothing anyone will see. The same
         * number `DefaultImagePreparer` uses.
         */
        const val MAX_EDGE_PX = 2048
        const val JPEG_QUALITY = 86
        const val MIME_JPEG = "image/jpeg"
        const val MIME_GIF = "image/gif"
        const val MIME_OCTET = "application/octet-stream"
    }
}
