package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.designsys.component.BrowserLinks
import io.github.plaza.designsys.component.rememberBrowserLinks

/**
 * The app's handler and prefetcher, swapped in over the platform ones at the composition root.
 *
 * The Custom Tab itself is `:designsys`'s
 * [io.github.plaza.designsys.component.CustomTabUriHandler], and the warm browser connection behind
 * it is that module's `CustomTabsWarmer`. What is here is the one decision neither can make: which
 * URIs they may take.
 *
 * There is no setting on top of this any more. The one that used to be here offered 应用内浏览 and
 * 系统浏览器, and the first of those read as "the app's own WebView" — which is not what it was:
 * both branches are the browser, differing only in whether it arrives stacked on this task. The app
 * has no in-app browser to offer instead, because the WebView it does have carries the NodeSeek
 * session and refuses a stranger's page by design, so a switch between two shades of "the browser"
 * was a promise the app could not keep. A Custom Tab is the better of the two for every link in a
 * thread, and it is now simply what happens.
 */
@Composable
internal fun rememberBrowserLinks(): BrowserLinks = rememberBrowserLinks(::usesCustomTab)

/**
 * Custom Tabs speak http(s) only; every other scheme is somebody else's app.
 *
 * `mailto:`, `tg://` and `otpauth://` therefore fall through to the platform handler, which still
 * throws when nothing on the device can take the link — callers such as the 两步验证 screen read that
 * failure to tell the user so. So does a device whose browser has no Custom Tabs support at all: the
 * extras are ignored and an ordinary browser tab opens, which is the same outcome the retired
 * 系统浏览器 setting used to name.
 *
 * nodeseek.com is not filtered out here even though the app routes its own pages to the session's
 * web view instead: that routing happens upstream in `Navigation`, and the few site URLs that
 * deliberately reach a browser anyway — an image being shared, the terms of service — should still
 * get a Custom Tab rather than a bare browser task.
 */
internal fun usesCustomTab(uri: String): Boolean = NodeSeekSite.isExternalWebUrl(uri)
