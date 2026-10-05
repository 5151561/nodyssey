package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.TitleBlockRule
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 屏蔽 › 标题: the reader's own title rules, plain words and regular expressions in one list.
 *
 * Device-side, unlike the user tab beside it — nothing here reaches the account. Every edit goes
 * straight to the store and the feeds re-query on their own; there is no save button.
 */
class TitleBlockViewModel(
    private val store: TitleKeywordStore,
) : ViewModel() {
    private val form = MutableStateFlow(TitleBlockForm())

    val uiState: StateFlow<TitleBlockUiState> =
        combine(store.blockRules, form) { rules, typed ->
            TitleBlockUiState(
                rules = rules,
                input = typed.input,
                isRegex = typed.isRegex,
                invalidPattern = typed.isRegex && typed.input.isNotBlank() && TitleKeywordStore.normalizePattern(typed.input) == null,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TitleBlockUiState(),
        )

    fun onInputChange(value: String) =
        form.update {
            it.copy(input = value.take(if (it.isRegex) TitleKeywordStore.MAX_PATTERN_LENGTH else TitleKeywordStore.MAX_KEYWORD_LENGTH))
        }

    /** Switching back to a plain word trims what was typed to a keyword's length, the way typing it would have. */
    fun setRegex(enabled: Boolean) =
        form.update {
            it.copy(
                isRegex = enabled,
                input = if (enabled) it.input else it.input.take(TitleKeywordStore.MAX_KEYWORD_LENGTH),
            )
        }

    /** Adds what is typed and clears the field; a pattern that does not compile stays put to be fixed. */
    fun add() {
        val typed = form.value
        if (typed.input.isBlank()) return
        viewModelScope.launch {
            if (store.addBlockRule(typed.input, typed.isRegex)) form.update { it.copy(input = "") }
        }
    }

    fun remove(rule: TitleBlockRule) {
        viewModelScope.launch { store.removeBlockRule(rule) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { TitleBlockViewModel(container.titleKeywordStore) }
            }
    }
}

private data class TitleBlockForm(
    val input: String = "",
    val isRegex: Boolean = false,
)

data class TitleBlockUiState(
    val rules: List<TitleBlockRule> = emptyList(),
    val input: String = "",
    val isRegex: Boolean = false,
    /** True while the field holds a pattern that would not compile — 添加 is off and the field says why. */
    val invalidPattern: Boolean = false,
)
