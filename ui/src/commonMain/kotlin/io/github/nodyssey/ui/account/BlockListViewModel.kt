package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.data.account.BlockedUser
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_block_added
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
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
 * Blocking is the account's, not the device's: the list lives on the site, and the site is also what
 * decides which posts and comments come back marked as blocked. Nothing here keeps a block of its
 * own. 临时显示被屏蔽内容 is the one device-side thing on the page, and it is a *view* switch over
 * those marks — the site's own user menu has the same escape hatch and it lasts exactly as long as
 * the page does (see `SettingsRepository.showBlockedContent`).
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
                nameInput = state.nameInput,
                isBlocking = state.isBlocking,
                showBlockedContent = showBlocked,
                message = state.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BlockListUiState(),
        )

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        runCatchingExceptCancellation { account.blockedUsers() }
            .onSuccess { blocked ->
                local.update { it.copy(isLoading = false, blocked = blocked) }
            }.onFailure { throwable ->
                local.update {
                    it.copy(isLoading = false, message = throwable.toAccountMessage())
                }
            }
    }

    fun setShowBlockedContent(enabled: Boolean) = settings.setShowBlockedContent(enabled)

    fun onNameInputChange(value: String) = local.update { it.copy(nameInput = value) }

    /**
     * Blocks whoever holds the typed name, then re-reads the list rather than appending a guessed row.
     *
     * By name because the endpoint takes a name — and because that means the site is the only thing
     * that knows whether the name exists. A row invented here would sit in the list until the next
     * visit and then quietly vanish. The field is cleared only on acceptance: after a refusal the
     * reader gets the site's sentence and their own typing back.
     */
    fun block() {
        val target = local.value.nameInput.trim()
        if (target.isEmpty() || local.value.isBlocking) return
        viewModelScope.launch {
            local.update { it.copy(isBlocking = true) }
            runCatchingExceptCancellation { account.block(target) }
                .onSuccess {
                    reload()
                    local.update {
                        it.copy(
                            isBlocking = false,
                            nameInput = "",
                            message = AccountMessage.Info(Res.string.account_block_added),
                        )
                    }
                }.onFailure { throwable ->
                    local.update {
                        it.copy(isBlocking = false, message = throwable.toBlockMessage())
                    }
                }
        }
    }

    /** The site says why it refused a name — 用户不存在 and the like — so that sentence wins. */
    private fun Throwable.toBlockMessage(): AccountMessage =
        (this as? SiteException)?.detail?.let(AccountMessage::Detail) ?: toAccountMessage()

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
    val nameInput: String = "",
    val isBlocking: Boolean = false,
    val message: AccountMessage? = null,
)

data class BlockListUiState(
    val isLoading: Boolean = true,
    val blocked: List<BlockedUser> = emptyList(),
    val unblocking: BlockedUser? = null,
    /** What is typed into 添加屏蔽; owned here so a refused name survives the failure. */
    val nameInput: String = "",
    /** A 添加屏蔽 in flight, so the field can refuse a second submit of the same name. */
    val isBlocking: Boolean = false,
    val showBlockedContent: Boolean = false,
    val message: AccountMessage? = null,
)
