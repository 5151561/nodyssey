package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nodyssey.data.settings.AppLanguage
import platform.Foundation.NSUserDefaults

/**
 * Writes the choice into `AppleLanguages`, which is where iOS keeps a per-app language.
 *
 * Not for the screen — `ProvideAppLanguage` answers that out of composition state and puts a change
 * on screen at once. This is for everything iOS renders on the app's behalf and reads its own
 * locale for: the system's own alert and share sheets, date and number formatting, and the language
 * a `WKWebView` announces. The system reads this key when the process starts and never re-reads it,
 * so those follow at the next launch — which is the platform's rule and not this app's.
 *
 * [AppLanguage.SYSTEM] removes the key rather than writing the device's current language into it:
 * the point of that entry is to keep following the device, and a tag frozen at the moment the user
 * chose "follow the system" would stop doing exactly that.
 *
 * There is no Traditional Chinese narrowing here, unlike the Android actual. It would have to be
 * written into this same key to take effect, and that would freeze the very setting it is applied
 * on behalf of. On screen it does not matter — `ProvideAppLanguage` narrows a `zh-Hant-HK` reader
 * to `values-zh-rTW` for every platform alike, and that is where the app's own words come from.
 */
@Composable
actual fun ApplyAppLanguage(language: AppLanguage?) {
    // Null means the store has not answered yet; see the note on the expect. Removing the key on
    // that first composition would read as 跟随系统 and throw away a choice already on disk.
    if (language == null) return
    LaunchedEffect(language) {
        val defaults = NSUserDefaults.standardUserDefaults
        val tag = language.tag
        if (tag == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(listOf(tag), APPLE_LANGUAGES)
        }
    }
}

/** The `NSUserDefaults` key iOS itself reads; the name is the platform's, not ours. */
private const val APPLE_LANGUAGES = "AppleLanguages"
