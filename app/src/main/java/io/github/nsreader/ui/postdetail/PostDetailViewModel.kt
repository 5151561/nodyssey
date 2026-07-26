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
import io.github.nsreader.data.session.SessionState
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.PostContent
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
    session: StateFlow<SessionState> = MutableStateFlow(SessionState()),
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Reconcile session provenance before collecting Room. A restored detail destination can be
        // the first entry composed after process death, so relying on the list ViewModel to do this
        // would allow an authenticated snapshot to flash on a signed-out cold start.
        viewModelScope.launch {
            repository.reconcileSession(
                isSignedIn = session.value.isSignedIn,
                fingerprint = session.value.fingerprint,
            )
            repository.thread(postId).collect { thread ->
                if (thread == null) {
                    // Logout deletes the Room row while this Navigation 3 entry can remain alive in a
                    // background tab. Clear the mirrored UI state as well or the composable would keep
                    // rendering the last authenticated snapshot after its owner was removed.
                    _uiState.update {
                        it.copy(
                            title = "",
                            body = null,
                            comments = emptyList(),
                            page = 1,
                            totalPages = 1,
                            hasNextPage = false,
                        )
                    }
                    return@collect
                }

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
            }
        }

        // Coming back from the WebView signed in, or with a challenge cleared, is the one case where a
        // thread that was "fresh" a second ago is worth re-fetching: a locked thread has content now.
        // `drop(1)` skips the cookies we started with — a cold start is not a session change.
        session
            .distinctUntilChangedBy { it.generation }
            .drop(1)
            .onEach { sessionState ->
                val cleared =
                    repository.reconcileSession(
                        isSignedIn = sessionState.isSignedIn,
                        fingerprint = sessionState.fingerprint,
                    )
                if (sessionState.isSignedIn && !cleared) repository.invalidateCaches()
                refresh()
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

    fun loadPage(page: Int) {
        val state = _uiState.value
        if (page < 1 || page > state.totalPages || state.isLoading || state.isAppending) return
        load(page = page, append = page != 1)
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
                initializer {
                    PostDetailViewModel(
                        postId,
                        container.postRepository,
                        container.sessionRepository.state,
                    )
                }
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
