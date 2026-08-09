package io.github.bbs1.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.net.ApiReply
import io.github.bbs1.net.ApiTopicDetail
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.ui.common.ApiErrorUi
import io.github.bbs1.ui.common.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @property error Same two-face rule as the feed: over an empty screen it is the whole screen, under
 *   loaded replies it is the footer of the list.
 */
data class TopicUiState(
    val loading: Boolean = true,
    val topic: ApiTopicDetail? = null,
    val replies: List<ApiReply> = emptyList(),
    val replyCount: Int = 0,
    val hasNextPage: Boolean = false,
    val appending: Boolean = false,
    val error: ApiErrorUi? = null,
)

/**
 * One thread on one site. Takes both as constructor values because neither may change under an open
 * thread: switching the current site swaps the whole back stack's Home, and this screen keeps
 * showing what was opened until popped.
 */
class TopicViewModel(
    private val api: Bbs1Api,
    private val baseUrl: String,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    init {
        refresh()
    }

    fun refresh() {
        nextPage = 1
        _uiState.value = TopicUiState(loading = true)
        viewModelScope.launch {
            try {
                val page = api.topic(baseUrl, topicId, 1)
                nextPage = 2
                _uiState.value = TopicUiState(
                    loading = false,
                    topic = page.topic,
                    replies = page.replies,
                    replyCount = page.replyCount,
                    hasNextPage = page.hasNextPage,
                )
            } catch (e: Bbs1ApiException) {
                _uiState.value = TopicUiState(loading = false, error = e.toUi())
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.appending || state.error != null || !state.hasNextPage) return
        _uiState.value = state.copy(appending = true)
        viewModelScope.launch {
            try {
                val page = api.topic(baseUrl, topicId, nextPage)
                nextPage++
                _uiState.value = _uiState.value.copy(
                    replies = _uiState.value.replies + page.replies,
                    replyCount = page.replyCount,
                    hasNextPage = page.hasNextPage,
                    appending = false,
                )
            } catch (e: Bbs1ApiException) {
                _uiState.value = _uiState.value.copy(appending = false, error = e.toUi())
            }
        }
    }

    /** Clears a footer error so the next scroll to the end retries the append. */
    fun retryAppend() {
        val state = _uiState.value
        if (state.replies.isEmpty() && state.topic == null) return
        if (state.error == null) return
        _uiState.value = state.copy(error = null)
        loadMore()
    }
}
