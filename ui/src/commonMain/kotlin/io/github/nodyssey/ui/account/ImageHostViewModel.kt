package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.imagehost.ConfigProblem
import io.github.nodyssey.data.imagehost.CustomHostFields
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.imagehost_deleted
import io.github.nodyssey.ui.resources.imagehost_key_cleared
import io.github.nodyssey.ui.resources.imagehost_key_saved
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 图床.
 *
 * The screen exists because NodeSeek has no image host of its own: the forum stores Markdown, and
 * every inline picture on it is a link to somewhere else. Which somewhere is the user's decision —
 * six of them are on offer, from the one the forum's own extension uses to a server they run
 * themselves — so this screen is a *connection*, not a preference. It says which host is selected,
 * takes the credential that host wants, and then shows what is stored on the other end, because a
 * connection nobody can see the far side of is just a checkmark.
 *
 * The token is never echoed back into the field after it is saved. [ImageHostUiState.savedTokenMask]
 * is what the screen shows; the real value stays in [storedToken] here and in the settings store, so
 * a settings page left open on a desk does not display a working credential. Editing the address
 * without re-pasting the token is still allowed — an empty token field on save means "keep the one
 * you have", which is the only way that edit could work at all.
 */
class ImageHostViewModel(
    private val repository: ImageHostRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageHostUiState())
    val uiState: StateFlow<ImageHostUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The live credential for the selected host. Deliberately not part of [ImageHostUiState]. */
    private var storedToken: String = ""

    init {
        // Only the *selection* is collected. Collecting the configuration too would overwrite what
        // the user is typing every time the store re-emits, which it does on every save.
        repository.selected
            .distinctUntilChanged()
            .onEach { provider -> load(provider) }
            .launchIn(viewModelScope)
    }

    fun selectProvider(provider: ImageHostProvider) {
        if (provider == _uiState.value.provider) return
        viewModelScope.launch { repository.select(provider) }
    }

    fun updateSiteUrl(value: String) =
        _uiState.update { it.copy(siteUrlInput = value, problem = null) }

    fun updateToken(value: String) =
        _uiState.update { it.copy(tokenInput = value, problem = null) }

    fun updateCustom(transform: (CustomHostFields) -> CustomHostFields) =
        _uiState.update { it.copy(custom = transform(it.custom), problem = null) }

    fun toggleCustomFields() =
        _uiState.update { it.copy(customFieldsExpanded = !it.customFieldsExpanded) }

    fun save() {
        val state = _uiState.value
        // A blank field means "unchanged", not "erase" — see the class comment. Clearing a
        // credential is what 断开 is for, and it asks first.
        val config = state.toConfig(token = state.tokenInput.trim().ifBlank { storedToken })
        val problem = config.problem()
        if (problem != null) {
            _uiState.update { it.copy(problem = problem) }
            return
        }
        viewModelScope.launch {
            repository.save(config)
            storedToken = config.token
            _uiState.update {
                it.copy(
                    tokenInput = "",
                    connected = true,
                    credentialMask = config.secret.mask(),
                    siteUrlInput = config.siteUrl,
                    problem = null,
                )
            }
            refresh(announceOnSuccess = true)
        }
    }

    fun requestDisconnect() = _uiState.update { it.copy(confirmingDisconnect = true) }

    fun dismissDisconnect() = _uiState.update { it.copy(confirmingDisconnect = false) }

    fun confirmDisconnect() {
        val provider = _uiState.value.provider
        viewModelScope.launch {
            repository.disconnect(provider)
            loadJob?.cancel()
            storedToken = ""
            _uiState.update {
                it.copy(
                    confirmingDisconnect = false,
                    connected = false,
                    credentialMask = null,
                    tokenInput = "",
                    custom = if (provider == ImageHostProvider.CUSTOM) {
                        it.custom.copy(headerValue = "", formFields = "")
                    } else {
                        it.custom
                    },
                    images = emptyList(),
                    isLoadingImages = false,
                    imagesError = ImageHostError.NotConfigured,
                    message = AccountMessage.Info(Res.string.imagehost_key_cleared),
                )
            }
        }
    }

    fun refresh(announceOnSuccess: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            val config = state.toConfig(storedToken)
            val blocker = when {
                !config.provider.browsable -> ImageHostError.Unsupported
                !config.isConfigured -> ImageHostError.NotConfigured
                else -> null
            }
            if (blocker != null) {
                _uiState.update {
                    it.copy(
                        isLoadingImages = false,
                        images = emptyList(),
                        imagesError = blocker,
                        message = if (announceOnSuccess) {
                            AccountMessage.Info(Res.string.imagehost_key_saved)
                        } else {
                            it.message
                        },
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
                                AccountMessage.Info(Res.string.imagehost_key_saved)
                            } else {
                                it.message
                            },
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingImages = false,
                            imagesError = throwable.toImageHostError(),
                            // The save itself did land even when the listing did not: half these
                            // hosts authenticate the two calls differently, and silence here would
                            // read as "the token was rejected".
                            message = if (announceOnSuccess) {
                                AccountMessage.Info(Res.string.imagehost_key_saved)
                            } else {
                                it.message
                            },
                        )
                    }
                }
        }
    }

    fun requestDelete(item: HostedImage) = _uiState.update { it.copy(deleting = item) }

    fun dismissDelete() = _uiState.update { it.copy(deleting = null) }

    fun confirmDelete() {
        val target = _uiState.value.deleting ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = null) }
            runCatchingExceptCancellation { repository.delete(target) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            images = state.images.filterNot { it.id == target.id },
                            message = AccountMessage.Info(Res.string.imagehost_deleted),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = AccountMessage.Info(throwable.toImageHostError().messageRes()))
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun load(provider: ImageHostProvider) {
        val config = repository.config(provider).first()
        storedToken = config.token
        _uiState.update {
            it.copy(
                isLoading = false,
                provider = provider,
                siteUrlInput = config.siteUrl,
                // Blank, always: switching to a host shows whether it is connected, never with what.
                tokenInput = "",
                connected = config.isConfigured,
                credentialMask = config.secret.mask(),
                custom = config.custom,
                problem = null,
                images = emptyList(),
                imagesError = null,
            )
        }
        refresh()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImageHostViewModel(container.imageHostRepository) }
        }
    }
}

data class ImageHostUiState(
    val isLoading: Boolean = true,
    val provider: ImageHostProvider = ImageHostProvider.DEFAULT,
    val siteUrlInput: String = "",
    val tokenInput: String = "",
    /** Whether what is *stored* for this host is complete enough to upload with. */
    val connected: Boolean = false,
    /** `cfbe……ac3f`, or null when this host holds no secret. Never the credential itself. */
    val credentialMask: String? = null,
    val custom: CustomHostFields = CustomHostFields(),
    /** The six extra fields only 自定义图床 has; collapsed until asked for. */
    val customFieldsExpanded: Boolean = false,
    /** Set when a save was refused, and cleared by the next keystroke. */
    val problem: ConfigProblem? = null,
    val confirmingDisconnect: Boolean = false,
    val isLoadingImages: Boolean = false,
    val images: List<HostedImage> = emptyList(),
    val imagesError: ImageHostError? = null,
    val deleting: HostedImage? = null,
    val message: AccountMessage? = null,
) {
    val totalBytes: Long get() = images.sumOf { it.sizeBytes }

    /**
     * The inputs as a configuration.
     *
     * [token] is passed in rather than read from [tokenInput] because the stored one is not on this
     * object — the screen must not be handed a live credential just so it can be validated.
     */
    internal fun toConfig(token: String) = ImageHostConfig(
        provider = provider,
        siteUrl = siteUrlInput,
        token = token,
        custom = custom,
    )
}

internal fun Throwable.toImageHostError(): ImageHostError =
    (this as? ImageHostException)?.error ?: ImageHostError.Network

/** Enough of the token to tell two of them apart, and far too little to use. */
private fun String.mask(): String? = when {
    isBlank() -> null
    length <= MASK_EDGE * 2 -> "*".repeat(length)
    else -> "${take(MASK_EDGE)}……${takeLast(MASK_EDGE)}"
}

private const val MASK_EDGE = 4
