package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * `UNUserNotificationCenter`, asked for the three things the app's notifications use.
 *
 * The same contract Android's actual keeps: called for the side effect, nothing returned, and a
 * denial leaves 通知 switched on — the badge is drawn by the app either way, so the setting and the
 * permission stay independent.
 *
 * The result is discarded rather than reported. Reading it back is what a screen would need to
 * *explain* a denial, and neither platform's actual does that today; the alternative would be a
 * callback Android has nothing to put in it.
 */
@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit =
    remember {
        {
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            ) { _, _ -> }
        }
    }
