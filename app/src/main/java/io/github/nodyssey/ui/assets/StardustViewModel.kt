package io.github.nodyssey.ui.assets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The transfer form as numbers, mirrored one way out of the fields that hold the text.
 *
 * The text itself lives in [StardustViewModel]'s `TextFieldState`s. Only the parsed values are needed
 * outside them — to enable 下一步, to work out the shortfall, and to read the transfer back on the
 * confirmation step — and a half-typed uid simply parses to null, which is the state the form is in
 * while the user is still typing.
 */
data class TransferForm(
    val amountValue: Int? = null,
    val recipientValue: Long? = null,
    val refValue: Long? = null,
) {
    val isComplete: Boolean get() = amountValue != null && recipientValue != null && refValue != null
}

data class StardustUiState(
    /** The profile call only. The ledger is a `PagingData` stream and reports its own load state. */
    val isLoadingBalance: Boolean = true,
    /**
     * A profile failure, which is why it does not blank the list.
     *
     * It still has to be visible somewhere: without a uid the ledger cannot be requested at all, so
     * this is the error the screen shows when there are no rows to show instead.
     */
    val error: NodeSeekError? = null,
    /** Needed to request the ledger, and to reach the site's own page, whose URL is per-member. */
    val uid: Long? = null,
    val balance: Int? = null,
    val transferOpen: Boolean = false,
    val confirmOpen: Boolean = false,
    val form: TransferForm = TransferForm(),
) {
    /** How far the balance falls short of the amount typed, or null when it covers it. */
    val shortfall: Int?
        get() {
            val amount = form.amountValue ?: return null
            val balance = balance ?: return null
            return (amount - balance).takeIf { it > 0 }
        }
}

/**
 * State holder for 星辰.
 *
 * Both halves are real now. The balance comes from the account endpoint; the ledger comes from
 * `/api/stardust/list`, whose contract was read out of the site's own bundle on 2026-07-30 — until
 * then this screen's list deliberately said "not wired" rather than guess. What has not moved into the
 * app is the *write*: sending stardust is irreversible, so the last step still happens on the site.
 *
 * The ledger cannot be requested before the profile call answers, because the endpoint is per-member
 * and there is no "me" form of it. So the pager hangs off the uid rather than starting in `init`.
 */
class StardustViewModel(
    private val profileRepository: ProfileRepository,
    private val stardustRepository: StardustRepository,
) : ViewModel() {
    val amount = TextFieldState()
    val recipientUid = TextFieldState()
    val refId = TextFieldState()

    private val _uiState = MutableStateFlow(StardustUiState())
    val uiState: StateFlow<StardustUiState> = _uiState.asStateFlow()

    /** Bumped on retry so the pager restarts even when the uid it depends on has not changed. */
    private val ledgerKey = MutableStateFlow<LedgerKey?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<PagingData<StardustEntry>> =
        ledgerKey
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { key ->
                Pager(
                    PagingConfig(
                        pageSize = NodeSeekJsonClient.STARDUST_PAGE_SIZE,
                        initialLoadSize = NodeSeekJsonClient.STARDUST_PAGE_SIZE,
                    ),
                ) { StardustPagingSource(stardustRepository, key.uid) }.flow
            }.cachedIn(viewModelScope)

    private var profileJob: Job? = null

    init {
        refresh()
        // One-way mirror of the parsed numbers, for 下一步's enabled state and the shortfall line.
        viewModelScope.launch {
            snapshotFlow { parseForm() }.collect { form -> _uiState.update { it.copy(form = form) } }
        }
    }

    fun refresh() {
        profileJob?.cancel()
        profileJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingBalance = true, error = null) }
                runCatchingExceptCancellation { profileRepository.profile() }
                    .onSuccess { profile ->
                        _uiState.update {
                            it.copy(
                                isLoadingBalance = false,
                                error = null,
                                uid = profile.uid,
                                balance = profile.starCount,
                            )
                        }
                        ledgerKey.update { previous ->
                            LedgerKey(profile.uid, (previous?.attempt ?: 0) + 1)
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoadingBalance = false, error = throwable.toNodeSeekError())
                        }
                    }
            }
    }

    fun openTransfer() = _uiState.update { it.copy(transferOpen = true) }

    fun dismissTransfer() = _uiState.update { it.copy(transferOpen = false, confirmOpen = false) }

    fun requestConfirm() {
        // Parsed from the fields rather than read off the mirror: what the confirmation step shows
        // back has to be what is on screen at the moment 下一步 was tapped, and the mirror is a frame
        // behind. Writing it alongside `confirmOpen` also means the dialog cannot disagree with the
        // check that let it open.
        val form = parseForm()
        if (!form.isComplete) return
        _uiState.update { it.copy(form = form, confirmOpen = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirmOpen = false) }

    /** Clears the form after the transfer has been handed to the site's own page. */
    fun transferHandedOff() {
        amount.clearText()
        recipientUid.clearText()
        refId.clearText()
        _uiState.update { it.copy(transferOpen = false, confirmOpen = false, form = TransferForm()) }
    }

    private fun parseForm(): TransferForm =
        TransferForm(
            amountValue = amount.text.toString().trim().toIntOrNull()?.takeIf { it > 0 },
            recipientValue = recipientUid.text.toString().trim().toLongOrNull()?.takeIf { it > 0 },
            refValue = refId.text.toString().trim().toLongOrNull()?.takeIf { it > 0 },
        )

    private data class LedgerKey(
        val uid: Long,
        val attempt: Int,
    )

    companion object {
        /** The cap every field on this form rejects past. */
        const val MAX_FIELD_LENGTH = 12

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    StardustViewModel(
                        profileRepository = container.profileRepository,
                        stardustRepository = container.stardustRepository,
                    )
                }
            }
    }
}

/**
 * Cursor paging, keyed on the id of the last row of the previous page.
 *
 * `before_id` is exclusive, so handing back the page's own last id is correct and cannot repeat a row.
 * `prevKey` is null because the endpoint's `after_id` would page *towards* newer rows and Paging would
 * use it to re-fetch pages it dropped — on an append-only ledger the head is the only thing that moves,
 * and refresh already starts there.
 */
internal class StardustPagingSource(
    private val repository: StardustRepository,
    private val memberId: Long,
) : PagingSource<Long, StardustEntry>() {
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, StardustEntry> =
        try {
            val page = repository.entries(memberId, beforeId = params.key)
            LoadResult.Page(
                data = page.entries,
                prevKey = null,
                // A "more" flag with no cursor to act on would loop on the same page forever, so both
                // have to be present for there to be a next key at all.
                nextKey = page.cursor.takeIf { page.hasMore && page.entries.isNotEmpty() },
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LoadResult.Error(throwable)
        }

    override fun getRefreshKey(state: PagingState<Long, StardustEntry>): Long? = null
}
