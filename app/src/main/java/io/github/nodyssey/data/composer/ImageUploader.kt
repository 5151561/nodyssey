package io.github.nodyssey.data.composer

import androidx.compose.runtime.Immutable
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostRepository

/** Where an attachment is in its trip to the image host. Board C5 draws one cell per value. */
enum class UploadStatus { WAITING, UPLOADING, UPLOADED, FAILED }

/**
 * Why an upload failed, reduced to the four answers that lead somewhere different.
 *
 * An enum rather than the host's exception because the editor is host-agnostic, and a string rather
 * than this would put wording in the data layer. The UI turns each of these into a sentence and,
 * for [NOT_CONFIGURED], into a link to 图床设置 — which is the whole reason the distinction exists.
 */
enum class UploadFailure { NOT_CONFIGURED, INVALID_KEY, REJECTED, CHALLENGE, NETWORK, UNKNOWN }

internal fun Throwable.toUploadFailure(): UploadFailure =
    when ((this as? ImageHostException)?.error) {
        ImageHostError.NotConfigured -> UploadFailure.NOT_CONFIGURED
        ImageHostError.InvalidKey -> UploadFailure.INVALID_KEY
        is ImageHostError.Rejected -> UploadFailure.REJECTED
        ImageHostError.Cloudflare -> UploadFailure.CHALLENGE
        ImageHostError.Network -> UploadFailure.NETWORK
        else -> UploadFailure.UNKNOWN
    }

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
    /**
     * Why this one failed.
     *
     * The tray cell is too small for a sentence, so it stays "失败 · 重试" and the editor's error
     * strip says the rest. Without it, a missing API key and a dead network look identical, and the
     * first is fixed in 账号设置 while the second is fixed by waiting.
     */
    val failure: UploadFailure? = null,
    /** The host's own sentence, when it gave one — more specific than [failure] can be. */
    val errorDetail: String? = null,
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
 * Still an interface: the host is a third-party service the app has no control over, and the editor
 * has no business knowing which one it is — the user picks it in 图床设置. [ImageHostUploader] is the
 * only implementation that ships, but tests substitute here rather than standing up an HTTP server.
 */
fun interface ImageUploader {
    /**
     * @param onProgress called with 0f–1f as bytes go out; the queue turns this into the ring in C5.
     * @return the URL to write into the Markdown.
     */
    suspend fun upload(
        attachment: ImageAttachment,
        onProgress: (Float) -> Unit,
    ): String
}

/**
 * Uploads to whichever host 图床设置 has selected.
 *
 * Two steps, and the first is the one that matters on a phone: [ImagePreparer] decodes the picked
 * URI at a bounded size and re-encodes it, so a 12-megapixel camera photo goes out as a few hundred
 * kilobytes instead of eight megabytes of someone's mobile data. Every host here would take the
 * original — their caps run from 5 MB to 100 MB — but nobody wants to spend a minute uploading a
 * forum reply, and the self-hosted ones are paying for that bandwidth themselves.
 *
 * Which host it is stays behind [ImageHostRepository]. This class does not branch on it, and neither
 * does anything above it.
 */
class ImageHostUploader(
    private val repository: ImageHostRepository,
    private val preparer: ImagePreparer,
) : ImageUploader {
    override suspend fun upload(
        attachment: ImageAttachment,
        onProgress: (Float) -> Unit,
    ): String {
        val prepared = preparer.prepare(attachment.source, attachment.name)
        return repository.upload(prepared, onProgress).url
    }
}
