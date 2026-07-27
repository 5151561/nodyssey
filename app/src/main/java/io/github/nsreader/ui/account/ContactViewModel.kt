package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.R
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.EndpointNotVerifiedException
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 联系方式 (d6 3/5) and the Telegram binding flow around it (f3).
 *
 * The email side follows the site's own two-step ritual — password first, then a code mailed to the
 * *new* address — instead of the app inventing a friendlier one, because the server is going to
 * demand those two proofs regardless and a form that asks for less can only fail later.
 *
 * The Telegram side is stitched across two apps: the bind itself happens inside Telegram, so this
 * ViewModel's whole job is what happens at the seams — confirm before leaving, poll after coming
 * back ([onResumed]), and give a manual 「刷新绑定状态」 for when the poll gives up before the user
 * finishes talking to the bot.
 */
class ContactViewModel(
    private val account: AccountSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    private var sendJob: Job? = null
    private var confirmJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.contact() }
                .onSuccess { contact ->
                    _uiState.update {
                        it.copy(isLoading = false, email = contact.email, emailVerified = contact.emailVerified)
                    }
                }.onFailure { throwable ->
                    val pending = throwable is EndpointNotVerifiedException
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            endpointPending = pending,
                            message =
                            if (pending) null else throwable.toAccountMessage(R.string.account_contact_section),
                        )
                    }
                }

            runCatchingExceptCancellation { account.telegramBinding() }
                .onSuccess { binding -> _uiState.update { it.copy(telegram = binding) } }
        }
    }

    // ---- 修改邮箱（站点两步流） ----

    fun toggleEmailChange() {
        _uiState.update { state ->
            if (state.changeExpanded) {
                // Collapsing abandons the draft: a half-typed password left in state would be pasted
                // back into the field the next time the flow opens.
                state.copy(changeExpanded = false, password = "", newEmail = "", code = "", codeSent = false)
            } else {
                state.copy(changeExpanded = true)
            }
        }
    }

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

    fun updateNewEmail(value: String) =
        _uiState.update {
            // Changing the target address invalidates a code that was mailed to the previous one.
            it.copy(newEmail = value.trim(), codeSent = false, code = "")
        }

    fun updateCode(value: String) = _uiState.update { it.copy(code = value.filter(Char::isDigit).take(EMAIL_CODE_LENGTH)) }

    fun sendCode() {
        if (sendJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canSendCode) return
        sendJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSendingCode = true, message = null) }
                runCatchingExceptCancellation { account.sendEmailChangeCode(state.password, state.newEmail) }
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isSendingCode = false,
                                codeSent = true,
                                message = AccountMessage.Info(R.string.account_email_code_sent),
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isSendingCode = false,
                                message = throwable.toAccountMessage(R.string.account_email_send_code),
                            )
                        }
                    }
            }
    }

    fun confirmEmailChange() {
        if (confirmJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canConfirmChange) return
        confirmJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isConfirming = true, message = null) }
                runCatchingExceptCancellation {
                    account.confirmEmailChange(state.password, state.newEmail, state.code)
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            isConfirming = false,
                            // The code came from the new mailbox, so the address is verified by
                            // construction — that is the entire point of the site's step ②.
                            email = state.newEmail,
                            emailVerified = true,
                            changeExpanded = false,
                            password = "",
                            newEmail = "",
                            code = "",
                            codeSent = false,
                            message = AccountMessage.Info(R.string.account_email_changed),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isConfirming = false,
                            message = throwable.toAccountMessage(R.string.account_email_change_confirm),
                        )
                    }
                }
            }
    }

    // ---- Telegram（f3） ----

    fun requestBind() = _uiState.update { it.copy(showBindDialog = true) }

    fun dismissBind() = _uiState.update { it.copy(showBindDialog = false) }

    /**
     * 「打开 Telegram」: asks the site for the bind link, then hands it to the screen through
     * [ContactUiState.bindUrlToOpen] — the ViewModel cannot start an Activity, and a callback caught
     * across the suspend point would fire into a composition that may already be gone.
     */
    fun confirmBind() {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.beginTelegramBinding() }
                .onSuccess { url ->
                    _uiState.update {
                        it.copy(showBindDialog = false, bindUrlToOpen = url, awaitingBinding = true)
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            showBindDialog = false,
                            message = throwable.toAccountMessage(R.string.account_telegram_bind),
                        )
                    }
                }
        }
    }

    fun consumeBindUrl() = _uiState.update { it.copy(bindUrlToOpen = null) }

    /**
     * Called on every ON_RESUME. Only does anything while a bind is in flight: the user left for
     * Telegram, so the answer they came back for is fetched on a short leash — a few polls, then the
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
                        runCatchingExceptCancellation { account.telegramBinding() }
                            .getOrElse { throwable ->
                                if (throwable is EndpointNotVerifiedException) {
                                    // Nine more identical failures teach us nothing.
                                    _uiState.update { it.copy(awaitingBinding = false) }
                                    return@launch
                                }
                                null
                            }
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
                            message = throwable.toAccountMessage(R.string.account_telegram_refresh),
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
                        it.copy(message = throwable.toAccountMessage(R.string.account_telegram_unbind))
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        /** Six digits, per the site's own placeholder (d6: "6 位数字"). */
        const val EMAIL_CODE_LENGTH = 6

        /** ~30 seconds of patience after returning from Telegram, then the manual refresh takes over. */
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
    val endpointPending: Boolean = false,
    val email: String = "",
    val emailVerified: Boolean = false,
    val changeExpanded: Boolean = false,
    val password: String = "",
    val newEmail: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val isSendingCode: Boolean = false,
    val isConfirming: Boolean = false,
    /** null until the (stubbed) contact endpoint has answered — the card shows neither state as a guess. */
    val telegram: TelegramBinding? = null,
    val showBindDialog: Boolean = false,
    val showUnbindDialog: Boolean = false,
    val isRefreshingBinding: Boolean = false,
    /** True from 「打开 Telegram」 until a poll or refresh settles the question. */
    val awaitingBinding: Boolean = false,
    /** One-shot: the bot deep link the screen should open, consumed via `consumeBindUrl`. */
    val bindUrlToOpen: String? = null,
    val message: AccountMessage? = null,
) {
    val isNewEmailMalformed: Boolean get() = newEmail.isNotEmpty() && !isEmailAddress(newEmail)

    val canSendCode: Boolean
        get() = !isSendingCode && password.isNotEmpty() && newEmail.isNotEmpty() && !isNewEmailMalformed

    val canConfirmChange: Boolean
        get() = !isConfirming && codeSent && code.length == ContactViewModel.EMAIL_CODE_LENGTH
}

/**
 * Enough of a check to catch a typo, and deliberately no more.
 *
 * A regex that tries to implement RFC 5322 rejects addresses that work; the server is the only thing
 * that knows whether an address is deliverable, and this only exists so an obvious slip is caught
 * before a verification mail goes to nobody.
 */
internal fun isEmailAddress(value: String): Boolean {
    val at = value.indexOf('@')
    if (at <= 0 || at != value.lastIndexOf('@')) return false
    val domain = value.substring(at + 1)
    return domain.length >= 3 && domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}
