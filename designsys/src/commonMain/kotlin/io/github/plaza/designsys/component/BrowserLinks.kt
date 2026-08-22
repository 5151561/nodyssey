package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.UriHandler

/**
 * The two halves of handing a link to the browser, built together because on Android they are one
 * connection: [uriHandler] opens the link and [prefetcher] is what got the browser ready for it.
 *
 * Kept as a pair rather than as two `remember`s a caller wires up separately, because two would mean
 * two bindings to the same browser service — and the session the prefetch goes through has to be the
 * session the tab is launched with, or the browser has no way to know the two are about the same
 * page.
 */
@Immutable
class BrowserLinks(
    val uriHandler: UriHandler,
    val prefetcher: LinkPrefetcher,
)

/**
 * The [UriHandler] a consumer swaps in over the platform one at the composition root, and the
 * [LinkPrefetcher] that belongs with it.
 *
 * The seam rather than the implementation is what is portable here. On Android it is a Custom Tab —
 * the browser doing the browsing, stacked on this task, so back lands on the thread the link came
 * from — and `androidx.browser` is Android-only; a platform that wants `SFSafariViewController`
 * provides its own over the same `LocalUriHandler`, and every `openUri` call site is already
 * pointing at it. See `AndroidCustomTabUriHandler.kt` for what [shouldUseCustomTab] decides and why
 * it has to be the caller's.
 *
 * A platform with no such notion answers with the one Compose already provides, which is what
 * "opening a link" means there, and a prefetcher that does nothing.
 */
@Composable
expect fun rememberBrowserLinks(shouldUseCustomTab: (String) -> Boolean): BrowserLinks
