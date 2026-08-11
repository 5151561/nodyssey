package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.UriHandler
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.settings.ExternalLinkTarget
import io.github.plaza.designsys.component.rememberExternalUriHandler

/**
 * The app's [UriHandler], swapped in over the platform one at the composition root.
 *
 * The Custom Tab itself is `:designsys`'s
 * [io.github.plaza.designsys.component.CustomTabUriHandler]. What is here is the one decision it
 * cannot make: which URIs it may take.
 */
@Composable
internal fun rememberExternalUriHandler(target: ExternalLinkTarget): UriHandler {
    val shouldUseCustomTab = remember(target) { { uri: String -> usesCustomTab(uri, target) } }
    return rememberExternalUriHandler(shouldUseCustomTab)
}

/**
 * Custom Tabs speak http(s) only; every other scheme is somebody else's app.
 *
 * `mailto:`, `tg://` and `otpauth://` therefore fall through to the platform handler, which still
 * throws when nothing on the device can take the link — callers such as the 两步验证 screen read that
 * failure to tell the user so.
 *
 * nodeseek.com is not filtered out here even though the app routes its own pages to the session's
 * web view instead: that routing happens upstream in `Navigation`, and the few site URLs that
 * deliberately reach a browser anyway — an image being shared, the terms of service — should still
 * get the Custom Tab the user asked for rather than a bare browser task.
 */
internal fun usesCustomTab(
    uri: String,
    target: ExternalLinkTarget,
): Boolean = target == ExternalLinkTarget.CUSTOM_TAB && NodeSeekSite.isExternalWebUrl(uri)
