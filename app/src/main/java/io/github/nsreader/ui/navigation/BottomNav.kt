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
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
fun NodeSeekNavigationBar(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    // v1 does not read notifications yet, so this is always zero — the slot exists so that turning
    // them on later is a data change rather than a layout change.
    unreadCount: Int = 0,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    val icon = if (selected) destination.selectedIcon else destination.icon
                    if (destination == TopLevelDestination.NOTIFICATIONS && unreadCount > 0) {
                        BadgedBox(badge = { Badge { Text(unreadCount.coerceAtMost(99).toString()) } }) {
                            Icon(icon, contentDescription = null)
                        }
                    } else {
                        Icon(icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(destination.label)) },
                colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
