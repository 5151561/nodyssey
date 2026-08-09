package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.data.account.TelegramBinding
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 联系方式 (d6 3/5) and the Telegram binding around it (f3).
 *
 * Two of this screen's actions are handed to the website rather than done here, and neither is a
 * shortcut: 修改邮箱 needs a Cloudflare Turnstile token before the site will mail a code, and 绑定
 * Telegram runs telegram.org's login widget in the page. Both need a browser. What is left native is
 * everything the app can actually finish — reading the address, reading the binding, and 解绑.
 *
 * So this ViewModel's real job is the seams: confirm before leaving, poll after coming back
 * ([onResumed]), and offer a manual 「刷新绑定状态」 for when the poll gives up first.
 */
class ContactViewModel(
    private val account: AccountSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.contact() }
                .onSuccess { contact ->
                    _uiState.update {
                        it.copy(isLoading = false, email = contact.email, emailVerified = contact.emailVerified)
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = throwable.toAccountMessage(),
                        )
                    }
                }

            runCatchingExceptCancellation { account.telegramBinding() }
                .onSuccess { binding -> _uiState.update { it.copy(telegram = binding) } }
        }
    }

    // ---- 修改邮箱（站点闭环） ----

    fun changeEmailOnSite() = openSite(NodeSeekSite.SETTING_CONTACT)

    // ---- Telegram（f3） ----

    fun requestBind() = _uiState.update { it.copy(showBindDialog = true) }

    fun dismissBind() = _uiState.update { it.copy(showBindDialog = false) }

    /** 「打开网页」: the site's contact tab is where the login widget lives. */
    fun confirmBind() {
        _uiState.update { it.copy(showBindDialog = false, awaitingBinding = true) }
        openSite(NodeSeekSite.SETTING_CONTACT)
    }

    fun consumeUrl() = _uiState.update { it.copy(urlToOpen = null) }

    /**
     * Called on every ON_RESUME. Only does anything while a bind is in flight: the user left for the
     * website, so the answer they came back for is fetched on a short leash — a few polls, then the
     * manual refresh takes over. Endless background polling against an authenticated endpoint is how
     * an app earns itself a Cloudflare challenge.
     */
    fun onResumed() {
        if (!_uiState.value.awaitingBinding) return
        if (pollJob?.isActive == true) return
        pollJob =
            viewModelScope.launch {
                repeat(BIND_POLL_ATTEMPTS) { attempt ->
                    if (attempt > 0) delay(BIND_POLL_INTERVAL_MILLIS)
                    val binding =
                        runCatchingExceptCancellation { account.telegramBinding() }.getOrNull()
                    if (binding != null) {
                        _uiState.update { it.copy(telegram = binding) }
                        if (binding.bound) {
                            _uiState.update {
                                it.copy(
                                    awaitingBinding = false,
                                    message = AccountMessage.Info(R.string.account_telegram_bound_toast),
                                )
                            }
                            return@launch
                        }
                    }
                }
            }
    }

    /** 「刷新绑定状态」— the manual fallback the f3 dialog offers under its buttons. */
    fun refreshBinding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingBinding = true) }
            runCatchingExceptCancellation { account.telegramBinding() }
                .onSuccess { binding ->
                    _uiState.update { state ->
                        state.copy(
                            isRefreshingBinding = false,
                            telegram = binding,
                            showBindDialog = if (binding.bound) false else state.showBindDialog,
                            awaitingBinding = state.awaitingBinding && !binding.bound,
                            message =
                            if (binding.bound) {
                                AccountMessage.Info(R.string.account_telegram_bound_toast)
                            } else {
                                AccountMessage.Info(R.string.account_telegram_still_unbound)
                            },
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRefreshingBinding = false,
                            message = throwable.toAccountMessage(),
                        )
                    }
                }
        }
    }

    fun requestUnbind() = _uiState.update { it.copy(showUnbindDialog = true) }

    fun dismissUnbind() = _uiState.update { it.copy(showUnbindDialog = false) }

    fun confirmUnbind() {
        viewModelScope.launch {
            _uiState.update { it.copy(showUnbindDialog = false) }
            runCatchingExceptCancellation { account.unbindTelegram() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            telegram = TelegramBinding(bound = false),
                            message = AccountMessage.Info(R.string.account_telegram_unbound_toast),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = throwable.toAccountMessage())
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /**
     * Publishes a URL for the screen to open. The ViewModel cannot start an Activity, and a callback
     * caught across a suspend point would fire into a composition that may already be gone.
     */
    private fun openSite(group: String) =
        _uiState.update {
            it.copy(urlToOpen = NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(group))
        }

    companion object {
        /** ~30 seconds of patience after returning from the site, then the manual refresh takes over. */
        const val BIND_POLL_ATTEMPTS = 10
        const val BIND_POLL_INTERVAL_MILLIS = 3_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ContactViewModel(container.accountSettingsRepository) }
            }
    }
}

data class ContactUiState(
    val isLoading: Boolean = true,
    val email: String = "",
    val emailVerified: Boolean = false,
    /** null until the binding has been read once — the card shows neither state as a guess. */
    val telegram: TelegramBinding? = null,
    val showBindDialog: Boolean = false,
    val showUnbindDialog: Boolean = false,
    val isRefreshingBinding: Boolean = false,
    /** True from 「打开网页」 until a poll or refresh settles the question. */
    val awaitingBinding: Boolean = false,
    /** One-shot: the page the screen should open, consumed via `consumeUrl`. */
    val urlToOpen: String? = null,
    val message: AccountMessage? = null,
)
