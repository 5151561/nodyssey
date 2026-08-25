package io.github.nodyssey

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.tools.AwardRoute
import io.github.nodyssey.ui.tools.AwardViewModel
import io.github.nodyssey.ui.tools.CommunityToolsScreen
import io.github.nodyssey.ui.tools.InviteRoute
import io.github.nodyssey.ui.tools.InviteViewModel
import io.github.nodyssey.ui.tools.LuckyRoute
import io.github.nodyssey.ui.tools.LuckyViewModel
import io.github.nodyssey.ui.tools.RulingRoute
import io.github.nodyssey.ui.tools.RulingViewModel

/**
 * 社区工具 and the boards behind it: 加精, 抽奖, 邀请, 仲裁, and 关于社区.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.toolsEntries(nav: StackEntryScope) = with(nav) {
    entry<CommunityToolsKey> {
        CommunityToolsScreen(
            onBack = { backStack.removeLastOrNull() },
            onAward = { backStack.add(AwardKey) },
            onProviders = { openWebUrl(NodeSeekSite.BASE_URL + NodeSeekSite.PROVIDERS_PATH) },
            onFriends = { openWebUrl(NodeSeekSite.BASE_URL + NodeSeekSite.FRIENDS_PATH) },
            onLucky = { backStack.add(LuckyKey) },
            onInvite = { backStack.add(InviteKey) },
            onRuling = { backStack.add(RulingKey) },
            onAboutCommunity = { backStack.add(AboutCommunityKey) },
        )
    }

    entry<AwardKey> {
        val viewModel: AwardViewModel =
            viewModel(factory = AwardViewModel.factory(container))
        AwardRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onPostClick = { backStack.add(PostDetailKey(it)) },
            // 加精 asks for a web view in one place only — the error state — so this is a
            // challenge to be cleared rather than a page to be browsed, and `openWebUrl`'s
            // MANAGE would have left it open with nothing saying the errand was finished.
            onOpenBrowser = { url ->
                backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
            },
            onSignIn = { backStack.add(SignInKey) },
        )
    }

    entry<LuckyKey> {
        val viewModel: LuckyViewModel =
            viewModel(factory = LuckyViewModel.factory(container))
        LuckyRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onOpenBrowser = openWebUrl,
        )
    }

    entry<InviteKey> {
        val viewModel: InviteViewModel =
            viewModel(factory = InviteViewModel.factory(container))
        InviteRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onBuyOnSite = { backStack.add(inviteWebKey(siteTitle)) },
        )
    }

    entry<RulingKey> {
        val viewModel: RulingViewModel =
            viewModel(factory = RulingViewModel.factory(container))
        RulingRoute(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            onPostClick = { postId, floor ->
                backStack.add(PostDetailKey(postId, floor = floor?.toString()))
            },
            onUserClick = openSpace,
            onOpenBrowser = openWebUrl,
            onVerify = { url ->
                backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
            },
            onSignIn = { backStack.add(SignInKey) },
        )
    }
}

/** Buying an invite code is a site-side spend; the app only confirms it. */
private fun inviteWebKey(title: String): WebKey =
    WebKey(NodeSeekSite.BASE_URL + NodeSeekSite.INVITE_PATH, title, WebViewGoal.MANAGE)
