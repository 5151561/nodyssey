package io.github.nodyssey

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.settings.AboutAppRoute
import io.github.nodyssey.ui.settings.AboutAppViewModel
import io.github.nodyssey.ui.settings.AboutCommunityScreen
import io.github.nodyssey.ui.settings.AppLinks
import io.github.nodyssey.ui.settings.ChangelogRoute
import io.github.nodyssey.ui.settings.ChangelogViewModel
import io.github.nodyssey.ui.settings.CommunityLinks
import io.github.nodyssey.ui.settings.DohSettingsRoute
import io.github.nodyssey.ui.settings.DohSettingsViewModel
import io.github.nodyssey.ui.settings.NotificationSettingsRoute
import io.github.nodyssey.ui.settings.NotificationSettingsViewModel
import io.github.nodyssey.ui.settings.OpenSourceLicensesScreen
import io.github.nodyssey.ui.settings.PrivacyRoute
import io.github.nodyssey.ui.settings.PrivacyViewModel
import io.github.nodyssey.ui.settings.ProxySettingsRoute
import io.github.nodyssey.ui.settings.ProxySettingsViewModel
import io.github.nodyssey.ui.settings.SettingsRoute
import io.github.nodyssey.ui.settings.SettingsViewModel
import io.github.nodyssey.ui.settings.theme.DynamicColorRoute
import io.github.nodyssey.ui.settings.theme.ThemeSettingsRoute
import io.github.nodyssey.ui.settings.theme.ThemeSettingsViewModel
import io.github.plaza.designsys.component.rememberSilentClipboardCopy

/**
 * 设置 and everything under it: appearance, notifications, network, and the 关于 pages.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.settingsEntries(nav: StackEntryScope) = with(nav) {
    entry<SettingsKey> {
        val viewModel: SettingsViewModel =
            viewModel(factory = SettingsViewModel.factory(container))
        SettingsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onOpenTheme = { backStack.add(ThemeSettingsKey) },
            onOpenNotifications = { backStack.add(NotificationSettingsKey) },
            onOpenProxy = { backStack.add(ProxySettingsKey) },
            onOpenDoh = { backStack.add(DohSettingsKey) },
            onOpenImageHost = { backStack.add(ImageHostKey) },
            onOpenAbout = { backStack.add(AboutAppKey) },
            onOpenLicenses = { backStack.add(OpenSourceLicensesKey) },
        )
    }

    entry<ThemeSettingsKey> {
        val viewModel: ThemeSettingsViewModel =
            viewModel(factory = ThemeSettingsViewModel.factory(container))
        ThemeSettingsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onOpenDynamicColor = { backStack.add(DynamicColorKey) },
        )
    }

    entry<DynamicColorKey> {
        val viewModel: ThemeSettingsViewModel =
            viewModel(factory = ThemeSettingsViewModel.factory(container))
        DynamicColorRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<NotificationSettingsKey> {
        val viewModel: NotificationSettingsViewModel =
            viewModel(factory = NotificationSettingsViewModel.factory(container))
        NotificationSettingsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            // 绑定 Telegram lives on 联系方式 (d6 3/4), the site's own binding entry.
            onOpenTelegram = { backStack.add(AccountContactKey) },
        )
    }

    entry<ProxySettingsKey> {
        val viewModel: ProxySettingsViewModel =
            viewModel(factory = ProxySettingsViewModel.factory(container))
        ProxySettingsRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<DohSettingsKey> {
        // Null on a platform that cannot apply a DoH server at all, where the row in 设置
        // that leads here is not drawn either. Nothing is drawn if it is reached some other
        // way, which is the honest answer for a screen whose every control would write a
        // setting nothing reads.
        container.doh?.let { doh ->
            val viewModel: DohSettingsViewModel =
                viewModel(factory = DohSettingsViewModel.factory(doh))
            DohSettingsRoute(
                viewModel = viewModel,
                onBack = { backStack.removeLastOrNull() },
            )
        }
    }

    entry<AboutAppKey> {
        val viewModel: AboutAppViewModel =
            viewModel(factory = AboutAppViewModel.factory(container))
        AboutAppRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onOpenChangelog = { backStack.add(ChangelogKey) },
            onOpenLicenses = { backStack.add(OpenSourceLicensesKey) },
            onOpenUri = openExternalUrl,
        )
    }

    entry<AboutCommunityKey> {
        val copyRss = rememberSilentClipboardCopy()
        AboutCommunityScreen(
            onBack = { backStack.removeLastOrNull() },
            onOpenAboutSite = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.ABOUT_PATH,
                        aboutSiteTitle,
                        WebViewGoal.MANAGE,
                    ),
                )
            },
            onOpenPrivacy = { backStack.add(PrivacyKey) },
            onOpenUri = { uri ->
                if (NodeSeekSite.isExternalWebUrl(uri)) {
                    openExternalUrl(uri)
                } else if (uri == CommunityLinks.EMAIL) {
                    runCatching { uriHandler.openUri(uri) }
                }
            },
            // The screen shows its own snackbar, so this is the copy that stays quiet.
            onCopyRss = { copyRss(rssLabel, CommunityLinks.RSS) },
        )
    }

    entry<PrivacyKey> {
        val privacyViewModel: PrivacyViewModel =
            viewModel(factory = PrivacyViewModel.factory(container))
        PrivacyRoute(
            viewModel = privacyViewModel,
            onBack = { backStack.removeLastOrNull() },
            // 「在浏览器中打开原文」 says the browser and means it — the terms are a public
            // page, and a reader checking what they agreed to may well want it outside the
            // app that is quoting it. The web view below is the fallback for when no
            // browser takes the intent.
            onOpenOriginal = {
                openExternalUrl(NodeSeekSite.BASE_URL + NodeSeekSite.TERMS_OF_SERVICE_PATH)
            },
            onOpenWebFallback = {
                backStack.add(
                    WebKey(
                        NodeSeekSite.BASE_URL + NodeSeekSite.TERMS_OF_SERVICE_PATH,
                        privacyTitle,
                        WebViewGoal.MANAGE,
                    ),
                )
            },
        )
    }

    entry<ChangelogKey> {
        val changelogViewModel: ChangelogViewModel =
            viewModel(factory = ChangelogViewModel.factory(container))
        ChangelogRoute(
            viewModel = changelogViewModel,
            onBack = { backStack.removeLastOrNull() },
            onOpenReleases = { openExternalUrl(AppLinks.RELEASES) },
            onOpenUri = { openExternalUrl(it) },
        )
    }

    entry<OpenSourceLicensesKey> {
        OpenSourceLicensesScreen(
            onBack = { backStack.removeLastOrNull() },
            onOpenUri = openExternalUrl,
        )
    }
}
