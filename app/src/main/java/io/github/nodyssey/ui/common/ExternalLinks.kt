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
 */
internal fun usesCustomTab(
    uri: String,
    target: ExternalLinkTarget,
): Boolean = target == ExternalLinkTarget.CUSTOM_TAB && NodeSeekSite.isExternalWebUrl(uri)
