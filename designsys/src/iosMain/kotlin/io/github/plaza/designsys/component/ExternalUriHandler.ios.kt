package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler

/**
 * The platform's own handler, which on iOS opens the link in Safari.
 *
 * `SFSafariViewController` is the thing this seam was named for — it is what a Custom Tab is on this
 * platform, down to landing back on the screen the link came from — and it is deliberately not here
 * yet. Presenting one needs a view controller to present *from*, and this app has no iOS shell to
 * ask for one; writing the presentation now would mean writing it against a host that does not
 * exist and cannot run it. [shouldUseCustomTab] is accepted and ignored meanwhile, exactly as the
 * desktop actual does, because what it answers is an app setting the caller reads either way.
 */
@Composable
actual fun rememberExternalUriHandler(shouldUseCustomTab: (String) -> Boolean): UriHandler =
    LocalUriHandler.current
