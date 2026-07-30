package io.github.nodyssey.ui.assets

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
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.CreditEntry
import io.github.nodyssey.data.CreditRepository
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The header above 鸡腿流水: the balance, and how far it is from the next level.
 *
 * Nothing here describes the list — the rows are a `PagingData` stream and carry their own load state,
 * so duplicating "is it loading" into this class would let the two disagree. What the header does own
 * is the one thing the ledger cannot tell us: the *current* balance. The newest row's running total is
 * usually the same number, but it is not the same fact, and it is missing entirely on an empty ledger.
 */
data class CreditUiState(
    val level: Int? = null,
    val chickenCount: Int? = null,
    /** Only Lv1's threshold is published, so this is null on every other level. */
    val nextLevelChicken: Int? = null,
)

/**
 * State holder for 鸡腿流水.
 *
 * The balance and the ledger load independently and are allowed to disagree about success: a failed
 * profile call leaves the header showing "—" over a perfectly good list, which is better than
 * replacing real rows with an error. Only the list failing is worth a full-screen error, and Paging
 * reports that itself.
 */
class CreditViewModel(
    private val assetsRepository: AssetsRepository,
    creditRepository: CreditRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreditUiState())
    val uiState: StateFlow<CreditUiState> = _uiState.asStateFlow()

    val entries: Flow<PagingData<CreditEntry>> =
        Pager(
            PagingConfig(
                pageSize = NodeSeekJsonClient.CREDIT_PAGE_SIZE,
                initialLoadSize = NodeSeekJsonClient.CREDIT_PAGE_SIZE,
            ),
        ) { CreditPagingSource(creditRepository) }.flow.cachedIn(viewModelScope)

    private var balanceJob: Job? = null

    init {
        refreshBalance()
    }

    fun refreshBalance() {
        balanceJob?.cancel()
        balanceJob =
            viewModelScope.launch {
                runCatchingExceptCancellation { assetsRepository.growth() }
                    .onSuccess { growth ->
                        _uiState.update {
                            it.copy(
                                level = growth.level,
                                chickenCount = growth.chickenCount,
                                nextLevelChicken = growth.nextLevelChicken,
                            )
                        }
                    }
                // No failure branch on purpose: the header's own null state already reads as "—",
                // and the list below it is the screen's real content and its own error surface.
            }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CreditViewModel(
                        assetsRepository = container.assetsRepository,
                        creditRepository = container.creditRepository,
                    )
                }
            }
    }
}

/**
 * Page numbers, because this is the one NodeSeek list that really is paged by number.
 *
 * `prevKey` stays null: the ledger is append-only at the head, so the only reason to walk backwards
 * would be to let Paging drop and re-fetch earlier pages, and re-reading page 1 after a sign-in would
 * shift every row down by one. Refresh restarts from the top instead.
 */
internal class CreditPagingSource(
    private val repository: CreditRepository,
) : PagingSource<Int, CreditEntry>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CreditEntry> =
        try {
            val page = params.key ?: 1
            val result = repository.page(page)
            LoadResult.Page(
                data = result.entries,
                prevKey = null,
                nextKey = if (result.hasNextPage) page + 1 else null,
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LoadResult.Error(throwable)
        }

    override fun getRefreshKey(state: PagingState<Int, CreditEntry>): Int? = null
}
