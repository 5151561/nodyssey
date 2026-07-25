package io.github.nsreader.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.ChallengeDetector
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.PostRepository
import io.github.nsreader.model.PostContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val postId: Long,
    private val repository: PostRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(page = 1, append = false)
    }

    fun refresh() = load(page = 1, append = false)

    /**
     * Comments are paginated on the site but read as one thread on a phone, so later pages are
     * appended to the same list instead of replacing it.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || !state.hasNextPage) return
        load(page = state.page + 1, append = true)
    }

    private fun load(page: Int, append: Boolean) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(isLoading = !append, isAppending = append, error = null, challenge = null)
        }
        loadJob = viewModelScope.launch {
            runCatching { repository.loadDetail(postId, page) }
                .onSuccess { detail ->
                    _uiState.update { state ->
                        state.copy(
                            title = detail.title,
                            body = detail.body,
                            comments = if (append) state.comments + detail.comments else detail.comments,
                            page = detail.page,
                            totalPages = detail.totalPages,
                            hasNextPage = detail.hasNextPage,
                            isLoading = false,
                            isAppending = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isAppending = false,
                            error = throwable.message ?: "加载失败",
                            challenge = (throwable as? NodeSeekException)?.challenge,
                        )
                    }
                }
        }
    }

    fun postUrl(): String = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId, _uiState.value.page)
}

data class PostDetailUiState(
    val title: String = "",
    val body: PostContent? = null,
    val comments: List<PostContent> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val error: String? = null,
    val challenge: ChallengeDetector.Challenge? = null,
)
