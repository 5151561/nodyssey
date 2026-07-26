package io.github.nsreader.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import io.github.nsreader.NotificationsKey
import io.github.nsreader.PostListKey
import io.github.nsreader.ProfileKey
import io.github.nsreader.R
import io.github.nsreader.SearchKey

/**
 * The four places the app can be.
 *
 * Selected items switch to the filled icon rather than only changing colour — Material 3's own
 * "expressive" signal, and the one that still reads at a glance on a small dark screen.
 */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(PostListKey, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH(SearchKey, R.string.tab_search, Icons.Filled.Search, Icons.Outlined.Search),
    NOTIFICATIONS(
        NotificationsKey,
        R.string.tab_notifications,
        Icons.Filled.Notifications,
        Icons.Outlined.Notifications,
    ),
    PROFILE(ProfileKey, R.string.tab_profile, Icons.Filled.Person, Icons.Outlined.Person),
    ;

    companion object {
        fun forKey(key: NavKey?): TopLevelDestination? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The four items, described once for whichever form the window calls for.
 *
 * `NavigationSuiteScaffold` picks the bar, the rail or the drawer from the actual window size, so
 * this deliberately does not build a `NavigationBar` itself — on a tablet or an unfolded foldable a
 * bar pinned to the bottom edge is both wrong and, under targetSdk 36, unavoidable to end up in.
 *
 * Icons carry no `contentDescription` on purpose: the item merges its label into one semantics node
 * and the label is already the accessible name, so describing the icon as well makes TalkBack say
 * everything twice.
 */
@Composable
fun NodeSeekNavigationItems(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    // v1 does not read notifications yet, so this is always zero — the slot exists so that turning
    // them on later is a data change rather than a layout change.
    unreadCount: Int = 0,
) {
    TopLevelDestination.entries.forEach { destination ->
        val selected = destination == current
        NavigationSuiteItem(
            selected = selected,
            onClick = { onSelect(destination) },
            icon = {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(destination.label)) },
            badge =
            if (destination == TopLevelDestination.NOTIFICATIONS && unreadCount > 0) {
                { Badge { Text(unreadCount.coerceAtMost(99).toString()) } }
            } else {
                null
            },
            // Colours are left at the Material defaults deliberately. The previous hand-written set
            // spelled out secondaryContainer / onSecondaryContainer / onSurfaceVariant — which is
            // exactly what the defaults already resolve to off this app's own colour scheme. Saying
            // it twice only created a second place for the rail and the bar to disagree.
        )
    }
}
