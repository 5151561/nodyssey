package io.github.nsreader.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.PostRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
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
            it.copy(isLoading = !append, isAppending = append, error = null)
        }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.loadDetail(postId, page) }
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
                            error = throwable.toNodeSeekError(),
                        )
                    }
                }
        }
    }

    fun postUrl(): String = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId, _uiState.value.page)

    companion object {
        fun factory(container: AppContainer, postId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { PostDetailViewModel(postId, container.postRepository) }
            }
    }
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
    val error: NodeSeekError? = null,
)
