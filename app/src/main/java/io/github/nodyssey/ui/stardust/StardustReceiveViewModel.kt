package io.github.nodyssey.ui.stardust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postdetail.ReactionFailure
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What one 收款码 knows beyond its own marker.
 *
 * [payerCount] and [received] are null until the tally arrives, and null is not zero: a code nobody
 * has paid says "0 人付款", while one whose tally has not loaded says nothing at all. Same for
 * [paidByMe] — "你未付款" is a claim, and the card must not make it before it knows.
 */
data class StardustReceiveUiState(
    val payerCount: Int? = null,
    val received: Int? = null,
    val paidByMe: Boolean? = null,
    val isLoading: Boolean = true,
    /** Why the tally could not be read. Distinct from [failure], which is a refused payment. */
    val error: SiteError? = null,
    val isPaying: Boolean = false,
    /** A refused payment, held until the card has shown it once. */
    val failure: ReactionFailure? = null,
    val isSignedIn: Boolean = false,
    val selfUid: Long? = null,
) {
    /** Whether [uid] is the account looking at the card — the payee's own code offers no button. */
    fun isSelf(uid: Long): Boolean = selfUid == uid
}

/**
 * One 收款码 inside a thread.
 *
 * Scoped per code — per payee *and* `ref_id` — because one post may carry several and each has its
 * own tally. Nothing here reaches Room: the marker is what gets cached, and who has paid since is
 * exactly the part that would go stale.
 *
 * Two reads rather than one. The site does the same, and the shapes differ: the first is everyone's
 * payments, the second is only this account's, and a signed-out reader can have neither.
 */
class StardustReceiveViewModel(
    private val node: RichNode.StardustReceive,
    private val repository: StardustRepository,
    session: Flow<SessionState>,
    /** Read for [ProfileRepository.selfUid] on every [load]; see `VoteViewModel` for why not once. */
    private val profile: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StardustReceiveUiState())
    val uiState: StateFlow<StardustReceiveUiState> = _uiState.asStateFlow()

    init {
        load()
        session
            .onEach { state -> _uiState.update { it.copy(isSignedIn = state.isSignedIn) } }
            .launchIn(viewModelScope)
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val self = profile.selfUid
            runCatchingExceptCancellation {
                val all = repository.receipts(memberId = node.memberId, refId = node.refId)
                // Only ask the second question when there is an account to ask it about. Signed out,
                // "have I paid" has no answer, and the card says nothing rather than "你未付款".
                val mine =
                    self?.let { repository.receipts(memberId = node.memberId, refId = node.refId, peerId = it) }
                all to mine
            }.onSuccess { (all, mine) ->
                _uiState.update {
                    it.copy(
                        payerCount = all.size,
                        // Only what came *in* counts as received; the same ref can carry a refund out.
                        received = all.filter { row -> row.diff > 0 }.sumOf { row -> row.diff },
                        paidByMe = mine?.isNotEmpty(),
                        isLoading = false,
                        error = null,
                        selfUid = self,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, error = throwable.toSiteError()) }
            }
        }
    }

    /**
     * Pays the code.
     *
     * The caller is the confirmation dialog, not the button: nothing here asks again, because by the
     * time this runs the reader has already been shown what leaves the account.
     */
    fun pay() {
        if (_uiState.value.isPaying) return
        val self = profile.selfUid ?: return
        _uiState.update { it.copy(isPaying = true, failure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation {
                repository.send(
                    recipientUid = node.memberId,
                    amount = node.amount,
                    refId = node.refId,
                    viewerUid = self,
                    onetime = node.onetime,
                )
            }.onSuccess {
                _uiState.update { it.copy(isPaying = false) }
                // Re-read rather than increment: the site is the only thing that knows whether a
                // one-off code accepted this, and a locally bumped count would claim it had.
                load()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isPaying = false,
                        failure =
                        ReactionFailure(
                            error = throwable.toSiteError(),
                            // "余额不足" and "已经支付过" say more than any wording of ours.
                            detail = (throwable as? SiteException)?.detail,
                        ),
                    )
                }
            }
        }
    }

    /** The failure has been shown; stop holding it. */
    fun onFailureShown() {
        _uiState.update { it.copy(failure = null) }
    }

    companion object {
        fun factory(
            container: AppContainer,
            node: RichNode.StardustReceive,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    StardustReceiveViewModel(
                        node = node,
                        repository = container.stardustRepository,
                        session = container.sessionRepository.state,
                        profile = container.profileRepository,
                    )
                }
            }
    }
}
