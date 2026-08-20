package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/** Nothing to ask for: the poll worker this setting drives is `WorkManager`, and that is Android's. */
@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit = {}
