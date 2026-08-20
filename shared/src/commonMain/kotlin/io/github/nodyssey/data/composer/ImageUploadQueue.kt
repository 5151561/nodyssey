package io.github.nodyssey.data.composer

import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** An image the picker handed back, before the queue has given it an identity. */
data class PickedImage(val source: String, val name: String)

/**
 * The attachment tray behind board C5, shared by the post editor and the reply editor.
 *
 * Uploads run **one at a time**. That is not a throttle for the server's benefit — it is what makes
 * the four states in the design real rather than decorative: with a single worker there is always
 * at most one "上传中" cell and the rest genuinely are "等待中". Uploading everything at once would
 * leave the waiting state unreachable and the progress rings racing each other for attention.
 *
 * Removal is allowed at any point, including mid-upload. The worker therefore re-checks that a cell
 * still exists before writing its result back, otherwise a finished upload would resurrect a row the
 * user has already dismissed.
 */
class ImageUploadQueue(
    private val scope: CoroutineScope,
    private val uploader: ImageUploader,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val _attachments = MutableStateFlow<List<ImageAttachment>>(emptyList())
    val attachments: StateFlow<List<ImageAttachment>> = _attachments.asStateFlow()

    /**
     * Emits each attachment as it lands, so the editor can insert the Markdown at that moment
     * rather than polling the list for new URLs.
     */
    private val _uploaded = MutableSharedFlow<ImageAttachment>(extraBufferCapacity = 16)
    val uploaded: SharedFlow<ImageAttachment> = _uploaded.asSharedFlow()

    private var worker: Job? = null

    fun enqueue(images: List<PickedImage>) {
        if (images.isEmpty()) return
        _attachments.update { current ->
            current + images.map { picked ->
                ImageAttachment(id = newId(), source = picked.source, name = picked.name)
            }
        }
        pump()
    }

    /** @return the removed attachment, so a caller can strip its Markdown out of the body. */
    fun remove(id: String): ImageAttachment? {
        val removed = _attachments.value.firstOrNull { it.id == id } ?: return null
        _attachments.update { current -> current.filterNot { it.id == id } }
        return removed
    }

    fun retry(id: String) {
        _attachments.update { current ->
            current.map { attachment ->
                if (attachment.id == id && attachment.status == UploadStatus.FAILED) {
                    attachment.copy(
                        status = UploadStatus.WAITING,
                        progress = 0f,
                        failure = null,
                        errorDetail = null,
                    )
                } else {
                    attachment
                }
            }
        }
        pump()
    }

    fun retryFailed() {
        _attachments.update { current ->
            current.map { attachment ->
                if (attachment.status == UploadStatus.FAILED) {
                    attachment.copy(
                        status = UploadStatus.WAITING,
                        progress = 0f,
                        failure = null,
                        errorDetail = null,
                    )
                } else {
                    attachment
                }
            }
        }
        pump()
    }

    fun clear() {
        worker?.cancel()
        worker = null
        _attachments.value = emptyList()
    }

    private fun pump() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = _attachments.value.firstOrNull { it.status == UploadStatus.WAITING } ?: break
                mutate(next.id) { it.copy(status = UploadStatus.UPLOADING, progress = 0f) }
                val result = runCatchingExceptCancellation {
                    uploader.upload(next) { progress ->
                        mutate(next.id) { it.copy(progress = progress.coerceIn(0f, 1f)) }
                    }
                }
                // Dismissed while it was in flight: neither outcome belongs on screen any more.
                if (_attachments.value.none { it.id == next.id }) continue
                result
                    .onSuccess { url ->
                        mutate(next.id) {
                            it.copy(status = UploadStatus.UPLOADED, progress = 1f, remoteUrl = url)
                        }
                        _attachments.value.firstOrNull { it.id == next.id }?.let { _uploaded.emit(it) }
                    }.onFailure { throwable ->
                        mutate(next.id) {
                            it.copy(
                                status = UploadStatus.FAILED,
                                failure = throwable.toUploadFailure(),
                                errorDetail = (throwable as? ImageHostException)?.detail,
                            )
                        }
                    }
            }
        }
    }

    private fun mutate(
        id: String,
        transform: (ImageAttachment) -> ImageAttachment,
    ) {
        _attachments.update { current ->
            current.map { attachment -> if (attachment.id == id) transform(attachment) else attachment }
        }
    }
}
