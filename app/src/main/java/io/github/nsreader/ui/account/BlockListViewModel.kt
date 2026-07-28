package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.R
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 屏蔽用户 (d6 4/5).
 *
 * Two owners meet on this page and stay separate: the blocked list is the site's (`#block`, endpoint
 * pending), while 临时显示被屏蔽内容 is the app's own session flag — the site keeps that escape hatch
 * in its user menu, d6 folds it in here, and it deliberately dies with the process
 * (see `SettingsRepository.showBlockedContent`).
 */
class BlockListViewModel(
    private val account: AccountSettingsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(BlockListLocalState())

    val uiState: StateFlow<BlockListUiState> =
        combine(local, settings.showBlockedContent) { state, showBlocked ->
            BlockListUiState(
                isLoading = state.isLoading,
                blocked = state.blocked,
                unblocking = state.unblocking,
                showBlockedContent = showBlocked,
                message = state.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BlockListUiState(),
        )

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.blockedUsers() }
                .onSuccess { blocked ->
                    local.update { it.copy(isLoading = false, blocked = blocked) }
                }.onFailure { throwable ->
                    local.update {
                        it.copy(isLoading = false, message = throwable.toAccountMessage())
                    }
                }
        }
    }

    fun setShowBlockedContent(enabled: Boolean) = settings.setShowBlockedContent(enabled)

    fun requestUnblock(user: BlockedUser) = local.update { it.copy(unblocking = user) }

    fun dismissUnblock() = local.update { it.copy(unblocking = null) }

    fun confirmUnblock() {
        val target = local.value.unblocking ?: return
        viewModelScope.launch {
            local.update { it.copy(unblocking = null) }
            runCatchingExceptCancellation { account.unblock(target.uid) }
                .onSuccess {
                    local.update { state ->
                        state.copy(blocked = state.blocked.filterNot { it.uid == target.uid })
                    }
                }.onFailure { throwable ->
                    local.update {
                        it.copy(message = throwable.toAccountMessage())
                    }
                }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BlockListViewModel(
                        account = container.accountSettingsRepository,
                        settings = container.settingsRepository,
                    )
                }
            }
    }
}

/** The half of the state this ViewModel owns; the show-blocked flag joins in from settings. */
private data class BlockListLocalState(
    val isLoading: Boolean = true,
    val blocked: List<BlockedUser> = emptyList(),
    val unblocking: BlockedUser? = null,
    val message: AccountMessage? = null,
)

data class BlockListUiState(
    val isLoading: Boolean = true,
    val blocked: List<BlockedUser> = emptyList(),
    val unblocking: BlockedUser? = null,
    val showBlockedContent: Boolean = false,
    val message: AccountMessage? = null,
)
