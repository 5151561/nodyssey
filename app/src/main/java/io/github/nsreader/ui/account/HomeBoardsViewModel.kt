package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.R
import io.github.nsreader.data.Board
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 首页版块 (d6 4/4).
 *
 * The only sub-page of 账号设置 that works end to end today, because it is not a site setting at all:
 * which boards the home strip shows is a presentation choice this app owns, stored in DataStore next
 * to the theme. That is deliberate — routing it through the server would make the strip depend on the
 * network to draw itself, and the site's `#homepage` group exists for the website's own layout.
 *
 * The selection is edited locally and written once on save, so backing out abandons the edit rather
 * than leaving the home strip half-changed.
 */
class HomeBoardsViewModel(
    private val settings: SettingsRepository,
    private val categories: CategoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeBoardsUiState())
    val uiState: StateFlow<HomeBoardsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { categories.refreshIfNeeded() }
        viewModelScope.launch {
            val stored = settings.settings.first().homeBoards
            categories.boards
                // Observed rather than taken once: on a cold start the board list arrives from Room
                // before the network refresh lands, and waiting for a populated list would leave the
                // screen spinning forever if that refresh never succeeded.
                .onEach { boards ->
                    // 综合 is not a board — no slug, never returned by the API, always on the strip.
                    // Offering it as a checkbox would let the user hide the front page.
                    val selectable = boards.filter { it.slug != null }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            boards = selectable,
                            // Seeded once, from the first non-empty list. Re-deriving on every emission
                            // would discard the user's ticks the moment a refresh landed underneath them.
                            selected =
                            if (state.seeded || selectable.isEmpty()) {
                                state.selected
                            } else {
                                visibleHomeBoards(selectable, stored).mapNotNull(Board::slug).toSet()
                            },
                            seeded = state.seeded || selectable.isNotEmpty(),
                        )
                    }
                }.launchIn(viewModelScope)
        }
    }

    fun toggle(slug: String) {
        _uiState.update { state ->
            val next = if (slug in state.selected) state.selected - slug else state.selected + slug
            // The last board cannot be unchecked: an empty strip would be indistinguishable from a
            // broken one, and the preference already spells "all" as the empty set.
            if (next.isEmpty()) {
                state.copy(message = AccountMessage.Info(R.string.account_home_boards_min))
            } else {
                state.copy(selected = next)
            }
        }
    }

    /** Back to unrestricted, which is the default rather than "everything ticked". */
    fun reset() {
        _uiState.update { state ->
            state.copy(selected = state.boards.mapNotNull(Board::slug).toSet())
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.selected.isEmpty()) return
        viewModelScope.launch {
            val allSlugs = state.boards.mapNotNull(Board::slug).toSet()
            // Storing "every board" as an explicit list would pin the strip to today's board list and
            // silently hide any board the site adds later. Unrestricted is stored as unrestricted.
            if (state.selected == allSlugs) {
                settings.clearHomeBoards()
            } else {
                settings.setHomeBoards(state.selected)
            }
            onSaved()
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeBoardsViewModel(
                        settings = container.settingsRepository,
                        categories = container.categoryRepository,
                    )
                }
            }
    }
}

data class HomeBoardsUiState(
    val isLoading: Boolean = true,
    val boards: List<Board> = emptyList(),
    val selected: Set<String> = emptySet(),
    val message: AccountMessage? = null,
    /** True once [selected] has been derived from the stored preference; see the init block. */
    internal val seeded: Boolean = false,
) {
    val total: Int get() = boards.size
}
