package io.github.nodyssey.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.report.ReportComparison
import io.github.nodyssey.data.settings.ReportCompareEntry
import io.github.nodyssey.data.settings.ReportCompareStore
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 测评对比: the basket as it stands, which of it is ticked, and those ticked reports lined up.
 *
 * Until the reader ticks anything, the newest [MAX_COMPARED] entries are the ones compared — the
 * reports just added from a thread are the ones they came here to see. The ticks are this screen's
 * own and are not stored: the basket is what persists, a choice among it is a moment's.
 */
class ReportCompareViewModel(
    private val store: ReportCompareStore,
) : ViewModel() {
    /** Null until the reader first ticks or unticks something. */
    private val picked = MutableStateFlow<List<ReportCompareEntry>?>(null)

    val uiState: StateFlow<ReportCompareUiState> =
        combine(store.entries, picked) { entries, pick ->
            // In basket order, whatever order they were ticked in, so a column keeps its place.
            val selected =
                if (pick == null) {
                    entries.takeLast(MAX_COMPARED)
                } else {
                    entries.filter { entry -> pick.any { it.isSameAs(entry) } }
                }
            ReportCompareUiState(
                loaded = true,
                entries = entries,
                selected = selected,
                comparison = selected.takeIf { it.size >= 2 }?.let { ReportComparison.of(it.map { entry -> entry.report }) },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportCompareUiState(),
        )

    /** Ticks or unticks [entry]; a fourth tick is refused, which the screen shows by disabling the rows. */
    fun toggle(entry: ReportCompareEntry) {
        val current = uiState.value.selected
        picked.update {
            when {
                current.any { it.isSameAs(entry) } -> current.filterNot { it.isSameAs(entry) }
                current.size < MAX_COMPARED -> current + entry
                else -> current
            }
        }
    }

    fun remove(entry: ReportCompareEntry) {
        viewModelScope.launch { store.remove(entry) }
    }

    companion object {
        /** Three columns is what a phone can hold beside each other and still read. */
        const val MAX_COMPARED = 3

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReportCompareViewModel(container.reportCompareStore) } }
    }
}

data class ReportCompareUiState(
    /** False until the store has answered once — an empty basket is not said before it is known. */
    val loaded: Boolean = false,
    val entries: List<ReportCompareEntry> = emptyList(),
    val selected: List<ReportCompareEntry> = emptyList(),
    /** Null while fewer than two are ticked. */
    val comparison: ReportComparison? = null,
) {
    fun isSelected(entry: ReportCompareEntry): Boolean = selected.any { it.isSameAs(entry) }
}
