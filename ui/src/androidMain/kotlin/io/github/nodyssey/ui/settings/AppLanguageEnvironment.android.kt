package io.github.nodyssey.ui.settings

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import io.github.nodyssey.data.settings.AppLanguage

/**
 * Hands the tree a [Configuration] carrying [language], which is what makes the change recompose.
 *
 * The lever is indirect and the whole of it is this. Compose Resources' default environment is
 * built from three things — `Locale.current`, `isSystemInDarkTheme()` and `LocalDensity` — and on
 * Android dark mode can only come from the configuration, so that default has to read
 * [LocalConfiguration]. Providing a configuration that differs therefore invalidates it, and
 * re-running it re-reads `LocaleList.getDefault()`, which the [remember] below has just set. The
 * strings on screen change with no activity destroyed, no black frame, and the scroll position and
 * back stack left alone.
 *
 * Nothing else about the configuration is touched: it is a copy of the one already in force with
 * the locales replaced, so `uiMode`, the window size and the font scale all still say what the
 * device says — `isSystemInDarkTheme()` included — and they keep saying it as the device changes.
 *
 * `AppLanguageRecompositionTest` is the assertion that this still works. It is a fair amount of
 * weight on one implementation detail, but a stable one: an environment that has to report a theme
 * qualifier on Android has nowhere else to read a theme from.
 */
@Composable
actual fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    // Derived from the configuration in force rather than from `context.resources.configuration`,
    // which is a snapshot: this one is re-read when the device rotates or the system flips to dark,
    // and a copy frozen at the first composition would hand the whole tree a stale `uiMode` and a
    // stale window size for as long as the language stayed put.
    val base = LocalConfiguration.current
    val configuration =
        remember(language, base) {
            val locales = AndroidAppLanguage.localesFor(language)
            // In composition rather than an effect: an effect runs after the tree below has already
            // resolved its strings, which would leave the first frame in the old language.
            LocaleList.setDefault(locales)
            Configuration(base).apply { setLocales(locales) }
        }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}

/** Android is the one platform that can change the language of a running composition. */
actual val appLanguageAppliesOnRestart: Boolean = false
