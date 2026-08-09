package io.github.bbs1.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.data.authed
import io.github.bbs1.net.ApiForum
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.ui.common.ApiErrorUi
import io.github.bbs1.ui.common.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * @property forums Only the ones this account may open a thread in — the server answers `can_post`
 *   per identity, so the picker never offers a board the post would bounce off.
 * @property createdTopicId One-shot: set once the thread exists, and the screen navigates to it.
 */
data class ComposeTopicUiState(
    val loading: Boolean = true,
    val forums: List<ApiForum> = emptyList(),
    val selectedForumId: Long? = null,
    val submitting: Boolean = false,
    val error: ApiErrorUi? = null,
    val createdTopicId: Long? = null,
)

/**
 * Writing a new thread on one site.
 *
 * The title and body live in the screen's own `TextFieldState`s rather than in here: they are edited
 * a keystroke at a time, and routing every one of those through a StateFlow would rebuild this state
 * object per character for no reader that needs it. What this owns is what survives a keystroke —
 * which boards are open to this account, which one is picked, and how the publish went.
 */
class ComposeTopicViewModel(
    private val api: Bbs1Api,
    private val repository: InstanceRepository,
    private val instanceId: String,
    private val baseUrl: String,
    /** The board the feed was filtered to when the composer opened, preselected when it is postable. */
    private val preferredForumId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComposeTopicUiState())
    val uiState: StateFlow<ComposeTopicUiState> = _uiState.asStateFlow()

    init {
        loadForums()
    }

    fun loadForums() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val postable = repository.authed(instanceId) { api.forums(baseUrl, token()) }.filter { it.canPost }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    forums = postable,
                    selectedForumId =
                    postable.firstOrNull { it.id == preferredForumId }?.id ?: postable.firstOrNull()?.id,
                )
            } catch (e: Bbs1ApiException) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.toUi())
            }
        }
    }

    fun selectForum(forumId: Long) {
        _uiState.value = _uiState.value.copy(selectedForumId = forumId)
    }

    fun submit(title: String, body: String) {
        val state = _uiState.value
        val forumId = state.selectedForumId ?: return
        if (state.submitting) return
        _uiState.value = state.copy(submitting = true, error = null)
        viewModelScope.launch {
            val token = token()
            if (token == null) {
                // Signed out while the draft was open — from another screen, or by a call this one
                // already made. Same state the server's own refusal would leave behind.
                _uiState.value = _uiState.value.copy(submitting = false, error = ApiErrorUi.Unauthorized)
                return@launch
            }
            try {
                val created =
                    repository.authed(instanceId) {
                        api.createTopic(baseUrl, token, forumId, title.trim(), body.trim())
                    }
                _uiState.value = _uiState.value.copy(submitting = false, createdTopicId = created.topicId)
            } catch (e: Bbs1ApiException) {
                // Length limits, the post interval, a board that closed since the picker loaded: all
                // of these come back as the server's own sentence, which is the one to show.
                _uiState.value = _uiState.value.copy(submitting = false, error = e.toUi())
            }
        }
    }

    fun consumeError() {
        if (_uiState.value.error != null) _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun token(): String? = repository.session(instanceId).first()?.token
}
