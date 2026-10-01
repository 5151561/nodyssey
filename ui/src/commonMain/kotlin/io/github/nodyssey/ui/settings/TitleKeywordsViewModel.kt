package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.TitleKeywordKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One list of title keywords — 屏蔽关键词 or 提醒关键词, the same screen for both because they are the
 * same thing put to two uses. Every edit goes straight to the store; there is no save button, and
 * the feeds re-query the moment a word lands.
 */
class TitleKeywordsViewModel(
    private val kind: TitleKeywordKind,
    private val store: TitleKeywordStore,
) : ViewModel() {
    private val input = MutableStateFlow("")

    val uiState: StateFlow<TitleKeywordsUiState> =
        combine(store.keywords(kind), input) { keywords, typed ->
            TitleKeywordsUiState(kind = kind, keywords = keywords, input = typed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TitleKeywordsUiState(kind = kind),
        )

    fun onInputChange(value: String) = input.update { value.take(TitleKeywordStore.MAX_KEYWORD_LENGTH) }

    /** Adds what is typed and clears the field; a blank or repeated word changes nothing. */
    fun add() {
        val typed = input.value
        if (typed.isBlank()) return
        viewModelScope.launch {
            store.add(kind, typed)
            input.value = ""
        }
    }

    fun remove(keyword: String) {
        viewModelScope.launch { store.remove(kind, keyword) }
    }

    companion object {
        fun factory(
            container: AppContainer,
            kind: TitleKeywordKind,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { TitleKeywordsViewModel(kind, container.titleKeywordStore) }
            }
    }
}

data class TitleKeywordsUiState(
    val kind: TitleKeywordKind,
    val keywords: List<String> = emptyList(),
    val input: String = "",
)
