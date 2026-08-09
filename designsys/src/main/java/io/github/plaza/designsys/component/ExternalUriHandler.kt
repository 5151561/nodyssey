package io.github.plaza.designsys.component

import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme

/**
 * A [UriHandler] that opens links in a Custom Tab, meant to be swapped in over the platform one at
 * the composition root.
 *
 * A forum thread is mostly other people's links, and every one of them otherwise starts a browser
 * task: read three links out of a thread and the app is three task switches behind whatever the
 * launcher shows. A Custom Tab is the browser doing the browsing — its process, its cookies, its
 * origin bar — but stacked on this task, so back lands on the thread it came from.
 *
 * Replacing the CompositionLocal rather than threading an opener through the screens is deliberate:
 * every call site that already says `LocalUriHandler.current.openUri(...)` is a link leaving the
 * app, including the ones Compose itself resolves for a `LinkAnnotation` inside post text. One
 * provider covers them all and nothing can be forgotten later.
 *
 * [shouldUseCustomTab] is what decides, per URI. It has to be the caller's: whether the feature is
 * even switched on is an app setting, and Custom Tabs speak http(s) only, so `mailto:`, `tg://` and
 * `otpauth://` belong to whichever app claimed the scheme. Everything it declines goes to
 * [fallback], which is also where a failed launch lands — and which still throws when nothing on the
 * device can take the link, because callers such as a 两步验证 screen read that failure to tell the
 * user so.
 */
class CustomTabUriHandler(
    private val context: Context,
    private val fallback: UriHandler,
    private val customTabsIntent: CustomTabsIntent,
    private val shouldUseCustomTab: (String) -> Boolean,
) : UriHandler {
    override fun openUri(uri: String) {
        if (shouldUseCustomTab(uri)) {
            // A browser without Custom Tabs support ignores the extras and opens a normal tab, so
            // no availability probe is needed — and probing would need a <queries> entry to work at
            // all on API 30+. runCatching covers the other outcome: nothing resolved the intent.
            val launched = runCatching { customTabsIntent.launchUrl(context, uri.toUri()) }.isSuccess
            if (launched) return
        }
        fallback.openUri(uri)
    }
}

/**
 * Builds the handler above against the theme that is actually on screen.
 *
 * The toolbar reads from [MaterialTheme] rather than a fixed brand colour so the tab matches the app
 * it opened from, including 动态取色. `setColorScheme` is pinned to the app's own resolved
 * [LocalPlazaDarkTheme] instead of `COLOR_SCHEME_SYSTEM`: 深色 and 定时 make the app dark while
 * the system stays light, and a white toolbar over a dark app is exactly the seam a Custom Tab is
 * supposed to hide.
 */
@Composable
fun rememberExternalUriHandler(shouldUseCustomTab: (String) -> Boolean): UriHandler {
    val context = LocalContext.current
    val fallback = LocalUriHandler.current
    val darkTheme = LocalPlazaDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme
    val customTabsIntent =
        remember(darkTheme, colorScheme) {
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                // The share sheet the browser already has. No custom menu item is added for it, and
                // none for "在浏览器中打开" either — the tab's own overflow menu carries that.
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .setColorScheme(
                    if (darkTheme) {
                        CustomTabsIntent.COLOR_SCHEME_DARK
                    } else {
                        CustomTabsIntent.COLOR_SCHEME_LIGHT
                    },
                ).setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams
                        .Builder()
                        .setToolbarColor(colorScheme.surface.toArgb())
                        .setSecondaryToolbarColor(colorScheme.surfaceContainer.toArgb())
                        .setNavigationBarColor(colorScheme.surface.toArgb())
                        .setNavigationBarDividerColor(colorScheme.outlineVariant.toArgb())
                        .build(),
                ).build()
        }
    return remember(context, fallback, customTabsIntent, shouldUseCustomTab) {
        CustomTabUriHandler(
            context = context,
            fallback = fallback,
            customTabsIntent = customTabsIntent,
            shouldUseCustomTab = shouldUseCustomTab,
        )
    }
}
