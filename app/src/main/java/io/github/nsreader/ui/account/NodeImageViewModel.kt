package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.R
import io.github.nsreader.core.NodeImageSite
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.nodeimage.NodeImageError
import io.github.nsreader.data.nodeimage.NodeImageException
import io.github.nsreader.data.nodeimage.NodeImageItem
import io.github.nsreader.data.nodeimage.NodeImageRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 图床 (NodeImage).
 *
 * The screen exists because NodeSeek has no image host of its own: the forum stores Markdown, and
 * every inline picture on it is a link to nodeimage.com. So the app needs a credential for a service
 * the user has a separate account on, and this is where that credential is entered, checked and
 * revoked — plus the list of what has already been uploaded, because an image host with no way to
 * see or delete what is on it is a place things get lost.
 *
 * The key is never echoed back into the field after it is saved. [maskedKey] is what the screen
 * shows, so a settings page left open on a desk does not display a working credential; replacing it
 * means pasting a new one, which is the same trade the site's own page makes.
 */
class NodeImageViewModel(
    private val repository: NodeImageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NodeImageUiState())
    val uiState: StateFlow<NodeImageUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        repository.apiKey
            .onEach { key ->
                _uiState.update {
                    it.copy(
                        isLoadingKey = false,
                        savedKeyMask = key?.mask(),
                    )
                }
            }.launchIn(viewModelScope)
        refresh()
    }

    fun updateKeyInput(value: String) {
        _uiState.update { it.copy(keyInput = value, keyInputError = false) }
    }

    fun saveKey() {
        val candidate = _uiState.value.keyInput.trim()
        if (!NodeImageSite.isPlausibleApiKey(candidate)) {
            _uiState.update { it.copy(keyInputError = true) }
            return
        }
        viewModelScope.launch {
            repository.setApiKey(candidate)
            // Cleared rather than kept: the field's job is done, and leaving a live key in a text
            // field is exactly what [maskedKey] exists to avoid.
            _uiState.update { it.copy(keyInput = "", keyInputError = false) }
            refresh(announceOnSuccess = true)
        }
    }

    fun requestClearKey() = _uiState.update { it.copy(confirmingClearKey = true) }

    fun dismissClearKey() = _uiState.update { it.copy(confirmingClearKey = false) }

    fun confirmClearKey() {
        viewModelScope.launch {
            repository.clearApiKey()
            loadJob?.cancel()
            _uiState.update {
                it.copy(
                    confirmingClearKey = false,
                    images = emptyList(),
                    isLoadingImages = false,
                    imagesError = NodeImageError.NotConfigured,
                    message = AccountMessage.Info(R.string.nodeimage_key_cleared),
                )
            }
        }
    }

    fun refresh(announceOnSuccess: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (repository.apiKey.first() == null) {
                _uiState.update {
                    it.copy(
                        isLoadingImages = false,
                        images = emptyList(),
                        imagesError = NodeImageError.NotConfigured,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoadingImages = true, imagesError = null) }
            runCatchingExceptCancellation { repository.images() }
                .onSuccess { images ->
                    _uiState.update {
                        it.copy(
                            isLoadingImages = false,
                            images = images,
                            imagesError = null,
                            message = if (announceOnSuccess) {
                                AccountMessage.Info(R.string.nodeimage_key_saved)
                            } else {
                                it.message
                            },
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingImages = false,
                            imagesError = throwable.toNodeImageError(),
                        )
                    }
                }
        }
    }

    fun requestDelete(item: NodeImageItem) = _uiState.update { it.copy(deleting = item) }

    fun dismissDelete() = _uiState.update { it.copy(deleting = null) }

    fun confirmDelete() {
        val target = _uiState.value.deleting ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = null) }
            runCatchingExceptCancellation { repository.delete(target.imageId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            images = state.images.filterNot { it.imageId == target.imageId },
                            message = AccountMessage.Info(R.string.nodeimage_deleted),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = AccountMessage.Info(throwable.toNodeImageError().messageRes()))
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NodeImageViewModel(container.nodeImageRepository) }
        }
    }
}

data class NodeImageUiState(
    val isLoadingKey: Boolean = true,
    /** `cfbe…ac3f`, or null when nothing is stored. Never the key itself. */
    val savedKeyMask: String? = null,
    val keyInput: String = "",
    /** True when what was typed is not shaped like a key at all — checked before any request. */
    val keyInputError: Boolean = false,
    val confirmingClearKey: Boolean = false,
    val isLoadingImages: Boolean = false,
    val images: List<NodeImageItem> = emptyList(),
    val imagesError: NodeImageError? = null,
    val deleting: NodeImageItem? = null,
    val message: AccountMessage? = null,
) {
    val hasKey: Boolean get() = savedKeyMask != null

    val totalBytes: Long get() = images.sumOf { it.sizeBytes }
}

internal fun Throwable.toNodeImageError(): NodeImageError =
    (this as? NodeImageException)?.error ?: NodeImageError.Network

/** Enough of the key to tell two of them apart, and far too little to use. */
private fun String.mask(): String =
    if (length <= MASK_EDGE * 2) "*".repeat(length) else "${take(MASK_EDGE)}……${takeLast(MASK_EDGE)}"

private const val MASK_EDGE = 4
