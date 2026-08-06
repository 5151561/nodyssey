package io.github.nodyssey.ui.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.canManage
import io.github.nodyssey.model.hasVoted
import io.github.nodyssey.ui.postdetail.ReactionFailure
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One option's voter list, as far as it has been expanded. */
data class VoterListState(
    val uids: List<Long> = emptyList(),
    val loadedPages: Int = 0,
    val isLoading: Boolean = false,
) {
    /** A short page is the last page — the endpoint reports no total. */
    val hasMore: Boolean get() = loadedPages > 0 && uids.size >= loadedPages * NodeSeekJsonClient.VOTER_PAGE_SIZE
}

data class VoteUiState(
    val vote: Vote? = null,
    val isLoading: Boolean = true,
    /** Why the vote could not be read. Distinct from [failure], which is a refused write. */
    val error: NodeSeekError? = null,
    /** Ticked but not yet submitted. At most one entry for a single-choice vote. */
    val selectedIds: Set<Long> = emptySet(),
    val isSubmitting: Boolean = false,
    /** A refused write, held until the card has shown it once. */
    val failure: ReactionFailure? = null,
    val isSignedIn: Boolean = false,
    val selfUid: Long? = null,
    val isAdmin: Boolean = false,
    val manageInFlight: Boolean = false,
    val deleted: Boolean = false,
    /** Expanded voter lists, keyed by option id. Absent means the reader has not opened it. */
    val voters: Map<Long, VoterListState> = emptyMap(),
) {
    val hasVoted: Boolean get() = vote?.hasVoted == true

    /**
     * Whether the results are visible at all.
     *
     * The site withholds them until this account has voted, and this app does the same. A locked vote
     * is not an exception: locking stops further votes, it does not publish the tally.
     */
    val showsResults: Boolean get() = hasVoted

    val canManage: Boolean get() = vote?.canManage(selfUid, isAdmin) == true

    /** Owners may lock. Only moderators may unlock — the site rejects anyone else. */
    val canLock: Boolean get() = canManage && vote?.locked == false

    val canUnlock: Boolean get() = isAdmin && vote?.locked == true

    val canDelete: Boolean get() = isAdmin && vote != null
}

/**
 * One vote inside a thread.
 *
 * Scoped per vote id, not per thread: a post can embed more than one, and each has its own request,
 * its own selection and its own failures.
 *
 * Nothing here reaches Room. See [VoteRepository] for why a vote is not cached.
 */
class VoteViewModel(
    private val voteId: Long,
    private val repository: VoteRepository,
    session: Flow<SessionState>,
    /**
     * Read for [ProfileRepository.selfUid] and [ProfileRepository.selfIsAdmin] only, and read again
     * on every [load]: both are in-memory session facts that may still be arriving when the thread
     * opens, so sampling once in `init` would hide the manage menu from the account that owns the
     * vote until it navigated away and back.
     */
    private val profile: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoteUiState())
    val uiState: StateFlow<VoteUiState> = _uiState.asStateFlow()

    init {
        load()
        session
            .onEach { state -> _uiState.update { it.copy(isSignedIn = state.isSignedIn) } }
            .launchIn(viewModelScope)
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.info(voteId) }
                .onSuccess { vote ->
                    _uiState.update { state ->
                        state.copy(
                            vote = vote,
                            isLoading = false,
                            error = null,
                            // The server's answer replaces any pending ticks: after a submit those
                            // ticks are now facts, and holding them separately would double-mark.
                            selectedIds = emptySet(),
                            voters = emptyMap(),
                            selfUid = profile.selfUid,
                            isAdmin = profile.selfIsAdmin,
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.toNodeSeekError()) }
                }
        }
    }

    /** Ticks or unticks an option. Single-choice replaces the selection rather than adding to it. */
    fun toggleSelection(itemId: Long) {
        val state = _uiState.value
        val vote = state.vote ?: return
        if (vote.locked || state.hasVoted || state.isSubmitting) return
        _uiState.update {
            val selected =
                when {
                    !vote.multiple -> setOf(itemId)
                    itemId in it.selectedIds -> it.selectedIds - itemId
                    else -> it.selectedIds + itemId
                }
            it.copy(selectedIds = selected)
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.selectedIds.isEmpty() || state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, failure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.submit(voteId, state.selectedIds.toList()) }
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    // Re-read rather than derive: the counts appear for the first time with this
                    // vote, so there is no local number to increment.
                    load()
                }.onFailure { throwable -> failWith(throwable) { it.copy(isSubmitting = false) } }
        }
    }

    fun setLocked(locked: Boolean) {
        if (_uiState.value.manageInFlight) return
        _uiState.update { it.copy(manageInFlight = true, failure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.setLocked(voteId, locked) }
                .onSuccess {
                    _uiState.update { it.copy(manageInFlight = false) }
                    load()
                }.onFailure { throwable -> failWith(throwable) { it.copy(manageInFlight = false) } }
        }
    }

    fun delete() {
        if (_uiState.value.manageInFlight) return
        _uiState.update { it.copy(manageInFlight = true, failure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.delete(voteId) }
                .onSuccess {
                    // No reload: there is nothing left to read, and asking would answer with a
                    // refusal that reads like the delete had failed.
                    _uiState.update { it.copy(manageInFlight = false, deleted = true, vote = null) }
                }.onFailure { throwable -> failWith(throwable) { it.copy(manageInFlight = false) } }
        }
    }

    /**
     * Opens (or extends) one option's voter list.
     *
     * The first page comes with the vote itself, so opening costs nothing; only "more" is a request.
     * Anonymous votes never get here — the card offers no handle to pull.
     */
    fun expandVoters(itemId: Long) {
        val state = _uiState.value
        val vote = state.vote ?: return
        if (!vote.isPublic || !state.showsResults) return
        val existing = state.voters[itemId]
        if (existing == null) {
            val seed = vote.items.firstOrNull { it.itemId == itemId }?.voters.orEmpty()
            _uiState.update {
                it.copy(voters = it.voters + (itemId to VoterListState(uids = seed, loadedPages = 1)))
            }
            return
        }
        if (existing.isLoading || !existing.hasMore) return
        _uiState.update {
            it.copy(voters = it.voters + (itemId to existing.copy(isLoading = true)))
        }
        viewModelScope.launch {
            val next = existing.loadedPages + 1
            runCatchingExceptCancellation { repository.voters(itemId, next) }
                .onSuccess { page ->
                    _uiState.update { current ->
                        val list = current.voters[itemId] ?: return@update current
                        current.copy(
                            voters =
                            current.voters +
                                (
                                    itemId to
                                        list.copy(
                                            // Distinct because a page boundary can repeat a uid
                                            // when someone votes while the list is open.
                                            uids = (list.uids + page).distinct(),
                                            loadedPages = next,
                                            isLoading = false,
                                        )
                                    ),
                        )
                    }
                }.onFailure {
                    _uiState.update { current ->
                        val list = current.voters[itemId] ?: return@update current
                        // Silent: a voter list that will not extend is a smaller loss than a
                        // snackbar over a thread the reader is still reading.
                        current.copy(voters = current.voters + (itemId to list.copy(isLoading = false)))
                    }
                }
        }
    }

    /** The failure has been shown; stop holding it. */
    fun onFailureShown() {
        _uiState.update { it.copy(failure = null) }
    }

    private inline fun failWith(
        throwable: Throwable,
        crossinline reset: (VoteUiState) -> VoteUiState,
    ) {
        _uiState.update {
            reset(it).copy(
                failure =
                ReactionFailure(
                    error = throwable.toNodeSeekError(),
                    // "You are not the owner of vote" says more than any wording of ours.
                    detail = (throwable as? NodeSeekException)?.detail,
                ),
            )
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            voteId: Long,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    VoteViewModel(
                        voteId = voteId,
                        repository = container.voteRepository,
                        session = container.sessionRepository.state,
                        profile = container.profileRepository,
                    )
                }
            }
    }
}
