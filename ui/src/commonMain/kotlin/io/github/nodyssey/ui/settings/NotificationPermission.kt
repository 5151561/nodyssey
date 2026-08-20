package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Asks for whatever this platform requires before it will show a notification, if anything.
 *
 * Called for its side effect when 通知 is switched on: Android 13+ shows nothing the user just asked
 * for until `POST_NOTIFICATIONS` is granted. A denial leaves polling on — the badge still updates —
 * so the setting and the permission stay independent, which is also why nothing is returned.
 */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit
