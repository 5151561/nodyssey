package io.github.nodyssey

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.account.AccountSettingsRoute
import io.github.nodyssey.ui.account.AccountSettingsViewModel
import io.github.nodyssey.ui.account.BlockListRoute
import io.github.nodyssey.ui.account.BlockListViewModel
import io.github.nodyssey.ui.account.ContactRoute
import io.github.nodyssey.ui.account.ContactViewModel
import io.github.nodyssey.ui.account.ImageHostRoute
import io.github.nodyssey.ui.account.ImageHostViewModel
import io.github.nodyssey.ui.account.PreferencesRoute
import io.github.nodyssey.ui.account.PreferencesViewModel
import io.github.nodyssey.ui.account.ProfileFieldsRoute
import io.github.nodyssey.ui.account.ProfileFieldsViewModel
import io.github.nodyssey.ui.account.SecurityRoute
import io.github.nodyssey.ui.account.SecurityViewModel
import io.github.nodyssey.ui.login.WebViewGoal

/**
 * 账号设置 and its sub-pages — the screens that write to the account on nodeseek.com itself.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.accountEntries(nav: StackEntryScope) = with(nav) {
    entry<AccountSettingsKey> {
        val viewModel: AccountSettingsViewModel =
            viewModel(factory = AccountSettingsViewModel.factory(container))
        AccountSettingsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onOpenProfileFields = { backStack.add(AccountProfileFieldsKey) },
            onOpenSecurity = { backStack.add(AccountSecurityKey) },
            onOpenContact = { backStack.add(AccountContactKey) },
            onOpenBlockList = { backStack.add(AccountBlockListKey) },
            // 常用偏好 and 首页版块 share one page (d6 5/5): three of their rows are the
            // same account-side switches, and splitting them would leave two stub screens.
            onOpenPreferences = { backStack.add(AccountPreferencesKey) },
        )
    }

    entry<ImageHostKey> {
        val viewModel: ImageHostViewModel =
            viewModel(factory = ImageHostViewModel.factory(container))
        ImageHostRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // Every image host is a different site with a different session; the in-app web
            // view exists to carry NodeSeek's cookies and has no business holding these.
            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
        )
    }

    entry<AccountProfileFieldsKey> {
        val viewModel: ProfileFieldsViewModel =
            viewModel(factory = ProfileFieldsViewModel.factory(container))
        ProfileFieldsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_INTRODUCTION),
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
        )
    }

    entry<AccountSecurityKey> {
        val viewModel: SecurityViewModel =
            viewModel(factory = SecurityViewModel.factory(container))
        SecurityRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // `otpauth://` belongs to whichever authenticator app registered for it, so it
            // leaves the app rather than being rendered here — the app never holds the
            // secret. Returns false when nothing claims the scheme, so the screen can say so
            // instead of appearing to do nothing.
            onOpenEnrolmentUri = { uri ->
                runCatching { uriHandler.openUri(uri) }.isSuccess
            },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_SECURITY),
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
        )
    }

    entry<AccountContactKey> {
        val viewModel: ContactViewModel =
            viewModel(factory = ContactViewModel.factory(container))
        ContactRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // 修改邮箱 and 绑定 Telegram are errands on nodeseek.com's own settings page, so
            // they go to the web view that shares the app's session. A Custom Tab would hand
            // them to the browser's cookie jar, where the account is not signed in.
            onOpenSite = { url, forBinding ->
                backStack.add(
                    WebKey(
                        url,
                        siteTitle,
                        if (forBinding) WebViewGoal.TELEGRAM_BIND else WebViewGoal.MANAGE,
                    ),
                )
            },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_CONTACT),
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
        )
    }

    entry<AccountBlockListKey> {
        val viewModel: BlockListViewModel =
            viewModel(factory = BlockListViewModel.factory(container))
        BlockListRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_BLOCK),
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
        )
    }

    entry<AccountPreferencesKey> {
        val viewModel: PreferencesViewModel =
            viewModel(factory = PreferencesViewModel.factory(container))
        PreferencesRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onSignIn = { backStack.add(SignInKey) },
            onVerify = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_PREFERENCE),
                        siteTitle,
                        WebViewGoal.CHALLENGE,
                    ),
                )
            },
        )
    }
}
