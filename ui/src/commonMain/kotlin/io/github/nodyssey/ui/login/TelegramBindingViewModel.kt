package io.github.nodyssey.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation

/**
 * The one question the web view asks while 绑定 Telegram is open in it: has the binding landed yet?
 *
 * A ViewModel because the alternative was what it replaces — a network request issued from a
 * navigation lambda, the only place in the app where the navigation layer touched the transport.
 * The polling loop itself stays in `WebViewScreen`, which owns when to ask; this owns what asking
 * means and what a failure is.
 */
class TelegramBindingViewModel(
    private val account: AccountSettingsRepository,
) : ViewModel() {
    /**
     * A failed poll means "not yet", never "give up": the page is still open and the user is still
     * working, so a hiccup on one request must not end the watch.
     */
    suspend fun isBound(): Boolean =
        runCatchingExceptCancellation { account.telegramBinding().bound }.getOrDefault(false)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TelegramBindingViewModel(account = container.accountSettingsRepository)
                }
            }
    }
}
