package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler

/**
 * The [UriHandler] a consumer swaps in over the platform one at the composition root.
 *
 * The seam rather than the implementation is what is portable here. On Android it is a Custom Tab —
 * the browser doing the browsing, stacked on this task, so back lands on the thread the link came
 * from — and `androidx.browser` is Android-only; a platform that wants `SFSafariViewController`
 * provides its own over the same `LocalUriHandler`, and every `openUri` call site is already
 * pointing at it. See `AndroidCustomTabUriHandler.kt` for what [shouldUseCustomTab] decides and why
 * it has to be the caller's.
 *
 * A platform with no such notion answers with the one Compose already provides, which is what
 * "opening a link" means there.
 */
@Composable
expect fun rememberExternalUriHandler(shouldUseCustomTab: (String) -> Boolean): UriHandler
