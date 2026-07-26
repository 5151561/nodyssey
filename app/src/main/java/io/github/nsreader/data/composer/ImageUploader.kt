package io.github.nsreader.data.composer

import androidx.compose.runtime.Immutable
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException

/** Where an attachment is in its trip to the image host. Board C5 draws one cell per value. */
enum class UploadStatus { WAITING, UPLOADING, UPLOADED, FAILED }

/**
 * One picked image on its way into the body.
 *
 * [source] is the content URI the picker handed us and stays put for the retry path; [remoteUrl] is
 * only non-null once the host has answered, and is what gets written into the Markdown.
 */
@Immutable
data class ImageAttachment(
    val id: String,
    val source: String,
    val name: String,
    val status: UploadStatus = UploadStatus.WAITING,
    /** 0f–1f, meaningful only while [status] is [UploadStatus.UPLOADING]. */
    val progress: Float = 0f,
    val remoteUrl: String? = null,
) {
    /**
     * What gets inserted into the body — and, on removal, deleted from it again.
     *
     * The display name comes from a content provider, which is allowed to answer with anything;
     * a `]` or `(` in it would end the alt text early and leave broken syntax the removal path
     * could no longer find.
     */
    val markdown: String?
        get() = remoteUrl?.let { url -> "![${name.replace(ALT_UNSAFE, " ").trim()}]($url)" }

    private companion object {
        val ALT_UNSAFE = Regex("""[\[\]()]""")
    }
}

/**
 * Uploads one image and reports progress.
 *
 * An interface rather than a concrete class because the host is the one part of the editor we have
 * not been able to observe: NodeSeek's editor says "NodeImage已就绪" and inline images come back as
 * `https://cdn.nodeimage.com/i/<id>.webp`, but the upload request itself has never been captured
 * from a signed-in session, and this sandbox cannot reach the site to find out (every request is
 * answered by Cloudflare). See [NodeImageUploader].
 */
fun interface ImageUploader {
    /**
     * @param onProgress called with 0f–1f as bytes go out; the queue turns this into the ring in C5.
     * @throws NodeSeekException when the upload cannot be completed.
     */
    suspend fun upload(
        attachment: ImageAttachment,
        onProgress: (Float) -> Unit,
    ): String
}

/**
 * The placeholder that keeps the failure honest.
 *
 * Every upload fails with a message saying the endpoint is not wired up, which is exactly what the
 * user sees: the queue moves the cell to [UploadStatus.FAILED] and offers a retry, the same path a
 * real network failure takes. Swapping in the real request is a one-class change once the
 * multipart shape has been captured on a device — nothing above this interface has to move.
 */
class NodeImageUploader : ImageUploader {
    override suspend fun upload(
        attachment: ImageAttachment,
        onProgress: (Float) -> Unit,
    ): String = throw NodeSeekException(
        error = NodeSeekError.Unknown,
        detail = UNAVAILABLE_DETAIL,
    )

    companion object {
        const val UNAVAILABLE_DETAIL = "NodeImage 上传接口尚未接入"
    }
}
