package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/**
 * The platform's own handler, which on iOS opens the link in Safari, and nothing to prefetch through.
 *
 * `SFSafariViewController` is the thing this seam was named for — it is what a Custom Tab is on this
 * platform, down to landing back on the screen the link came from — and it is deliberately not here
 * yet. Presenting one needs a view controller to present *from*, and this app has no iOS shell to
 * ask for one; writing the presentation now would mean writing it against a host that does not
 * exist and cannot run it. [shouldUseCustomTab] is accepted and ignored meanwhile, exactly as the
 * desktop actual does, because what it answers is an app setting the caller reads either way.
 *
 * The prefetcher goes the same way. Safari's own warming is `SFSafariViewController`'s to ask for and
 * there is nothing to ask yet.
 */
@Composable
actual fun rememberBrowserLinks(shouldUseCustomTab: (String) -> Boolean): BrowserLinks {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { BrowserLinks(uriHandler = uriHandler, prefetcher = {}) }
}
