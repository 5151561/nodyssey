package io.github.nsreader.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.PostRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.PostContent
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for one thread, reading from the database and writing through it.
 *
 * The thread is *observed*, never fetched-and-held: [PostRepository.thread] emits whatever Room holds,
 * so a cached thread paints on the first frame and offline it paints at all. A refresh writes into
 * Room and the new content arrives through the same observation — there is no second path by which
 * content reaches the screen.
 *
 * A failed refresh therefore does not blank the screen. The error is reported, but cached comments
 * stay visible underneath it, which is the behaviour that makes a flaky connection tolerable.
 */
class PostDetailViewModel(
    private val postId: Long,
    private val repository: PostRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        repository
            .thread(postId)
            .onEach { thread ->
                // Null means nothing is cached yet, so there is nothing to show and nothing to record.
                if (thread == null) return@onEach

                _uiState.update { state ->
                    state.copy(
                        title = thread.title,
                        body = thread.body,
                        comments = thread.comments,
                        page = thread.loadedPages,
                        totalPages = thread.totalPages,
                        hasNextPage = thread.hasNextPage,
                    )
                }

                // "Read" means content actually reached the screen — which is exactly this emission,
                // and the only signal that covers all the ways it can happen: a fresh fetch, a cache
                // hit that skipped the network, or a cached thread read in aeroplane mode.
                //
                // Marking read on *opening* instead looked equivalent and was not: opening an
                // uncached post offline shows nothing but an error, and the thread still ended up
                // dimmed in the list as though it had been read.
                repository.markThreadRead(postId)
            }.launchIn(viewModelScope)

        viewModelScope.launch {
            // A thread read moments ago needs no request; the observation above has already painted it.
            if (!repository.isThreadFresh(postId)) {
                load(page = 1, append = false)
            }
        }
    }

    fun refresh() = load(page = 1, append = false)

    /**
     * Comments are paginated on the site but read as one thread on a phone, so later pages are
     * appended to the same list rather than replacing it. The append itself happens in the database.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || !state.hasNextPage) return
        load(page = state.page + 1, append = true)
    }

    private fun load(
        page: Int,
        append: Boolean,
    ) {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = !append, isAppending = append, error = null) }
        loadJob =
            viewModelScope.launch {
                runCatchingExceptCancellation { repository.refreshThread(postId, page) }
                    .onSuccess {
                        // Neither content nor the read mark is applied here: both follow from the Room
                        // observation above, so there is exactly one path by which either can happen.
                        _uiState.update { it.copy(isLoading = false, isAppending = false) }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
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
        fun factory(
            container: AppContainer,
            postId: Long,
        ): ViewModelProvider.Factory =
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
