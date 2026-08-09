package io.github.bbs1.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.data.authed
import io.github.bbs1.model.InstanceSession
import io.github.bbs1.net.ApiReply
import io.github.bbs1.net.ApiTopicDetail
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.ui.common.ApiErrorUi
import io.github.bbs1.ui.common.toUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @property error Same two-face rule as the feed: over an empty screen it is the whole screen, under
 *   loaded replies it is the footer of the list.
 * @property canReply The server's answer for this identity — group permission, mute and a closed
 *   board all fold into it. Kept apart from [signedIn] so the bar can say which of the two is missing.
 * @property replyPosted One-shot: the composer closes on seeing it, then calls
 *   [TopicViewModel.consumeReplyPosted].
 */
data class TopicUiState(
    val loading: Boolean = true,
    val topic: ApiTopicDetail? = null,
    val replies: List<ApiReply> = emptyList(),
    val replyCount: Int = 0,
    val hasNextPage: Boolean = false,
    val appending: Boolean = false,
    val error: ApiErrorUi? = null,
    val signedIn: Boolean = false,
    val canReply: Boolean = false,
    val replySubmitting: Boolean = false,
    val replyError: ApiErrorUi? = null,
    val replyPosted: Boolean = false,
)

/**
 * One thread on one site. Takes the site as constructor values because neither may change under an
 * open thread: switching the current site swaps the whole back stack's Home, and this screen keeps
 * showing what was opened until popped.
 *
 * What it does watch is that site's credential. Signing in or out changes what the server says about
 * this very thread — `can_reply` is computed per request — so the thread reloads on the change
 * rather than keeping an answer that was given to somebody else.
 */
class TopicViewModel(
    private val api: Bbs1Api,
    private val repository: InstanceRepository,
    private val instanceId: String,
    private val baseUrl: String,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var session: InstanceSession? = null
    private var loadJob: Job? = null
    private var nextPage = 1

    init {
        viewModelScope.launch {
            // Distinct by contract, so this emits once at start — which is the first load — and again
            // only when the credential really changed.
            repository.session(instanceId).collect { current ->
                session = current
                _uiState.value = _uiState.value.copy(signedIn = current != null)
                refresh()
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        nextPage = 1
        _uiState.value = TopicUiState(loading = true, signedIn = session != null)
        loadJob = viewModelScope.launch {
            try {
                val page = repository.authed(instanceId) { api.topic(baseUrl, topicId, 1, session?.token) }
                nextPage = 2
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    topic = page.topic,
                    replies = page.replies,
                    replyCount = page.replyCount,
                    hasNextPage = page.hasNextPage,
                    canReply = page.canReply,
                )
            } catch (e: Bbs1ApiException) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.toUi())
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.appending || state.error != null || !state.hasNextPage) return
        _uiState.value = state.copy(appending = true)
        loadJob = viewModelScope.launch {
            try {
                val page =
                    repository.authed(instanceId) { api.topic(baseUrl, topicId, nextPage, session?.token) }
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

    fun submitReply(body: String) {
        val state = _uiState.value
        if (state.replySubmitting || body.isBlank()) return
        val token = session?.token
        if (token == null) {
            _uiState.value = state.copy(replyError = ApiErrorUi.Unauthorized)
            return
        }
        _uiState.value = state.copy(replySubmitting = true, replyError = null)
        viewModelScope.launch {
            try {
                val created =
                    repository.authed(instanceId) { api.createReply(baseUrl, token, topicId, body.trim()) }
                _uiState.value = _uiState.value.withPostedReply(created.reply)
            } catch (e: Bbs1ApiException) {
                _uiState.value = _uiState.value.copy(replySubmitting = false, replyError = e.toUi())
            }
        }
    }

    fun consumeReplyPosted() {
        if (_uiState.value.replyPosted) _uiState.value = _uiState.value.copy(replyPosted = false)
    }

    fun consumeReplyError() {
        if (_uiState.value.replyError != null) _uiState.value = _uiState.value.copy(replyError = null)
    }

    /**
     * Puts a just-saved reply where the thread's own ordering says it belongs, without a reload.
     *
     * `reply_create` does not number the floor — the server numbers those per page, from the total —
     * so it is computed here from the new total, which is the newest reply's floor under either
     * ordering. The one case that cannot be shown is an oldest-first thread whose last page is not
     * loaded: the reply exists, but it belongs on a page this screen has not fetched, so only the
     * count moves and scrolling to the end brings it in.
     */
    private fun TopicUiState.withPostedReply(reply: ApiReply): TopicUiState {
        val total = replyCount + 1
        val placed = reply.copy(floor = total)
        val newestFirst = topic?.replyOrder == 1
        val shown = when {
            newestFirst -> listOf(placed) + replies
            !hasNextPage -> replies + placed
            else -> replies
        }
        return copy(
            replies = shown,
            replyCount = total,
            replySubmitting = false,
            replyPosted = true,
        )
    }
}
