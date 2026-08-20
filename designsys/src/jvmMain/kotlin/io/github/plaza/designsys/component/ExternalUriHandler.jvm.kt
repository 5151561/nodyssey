package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler

/**
 * The platform's own handler, unchanged.
 *
 * A desktop has one browser and no notion of "in this task", so there is nothing for
 * [shouldUseCustomTab] to select between — the parameter is accepted and ignored rather than removed,
 * because what it answers is an app setting the caller reads either way.
 */
@Composable
actual fun rememberExternalUriHandler(shouldUseCustomTab: (String) -> Boolean): UriHandler =
    LocalUriHandler.current
