package io.github.nodyssey.data.imagehost

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody

/**
 * One image host's protocol, and nothing else.
 *
 * Stateless on purpose: the credential arrives as a [ImageHostConfig] parameter rather than being
 * held, so [ImageHostRepository] can own storage for all six hosts in one place and a client is
 * testable by handing it a config. These are plain blocking functions — the repository confines
 * every call to the IO dispatcher, so no implementation has to remember to.
 *
 * [images] and [delete] default to refusing. Two of the six hosts genuinely publish no such
 * endpoint (see [ImageHostProvider.browsable]), and a default that throws is what keeps that from
 * being expressed as an empty list the screen would draw as "you have no images".
 */
internal interface ImageHostClient {
    fun upload(
        config: ImageHostConfig,
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage

    fun images(config: ImageHostConfig): List<HostedImage> =
        throw ImageHostException(ImageHostError.Unsupported)

    fun delete(config: ImageHostConfig, image: HostedImage): Unit =
        throw ImageHostException(ImageHostError.Unsupported)
}

/** The file part every one of these hosts wants, under whichever name that host reads it from. */
internal fun MultipartBody.Builder.addImagePart(
    fieldName: String,
    upload: ImageHostUpload,
    onProgress: (Float) -> Unit,
): MultipartBody.Builder = addFormDataPart(
    fieldName,
    upload.fileName,
    // A host that answers 415 for a MIME type it dislikes is better than a crash here, so an
    // unparseable type falls back to the generic one rather than throwing on the way out.
    ProgressRequestBody(
        upload.bytes,
        upload.mimeType.toMediaTypeOrNull() ?: OCTET_STREAM,
        onProgress,
    ),
)

private val OCTET_STREAM = "application/octet-stream".toMediaTypeOrNull()!!
