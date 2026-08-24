package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nodyssey.data.settings.AppLanguage
import platform.Foundation.NSUserDefaults

/**
 * Writes the choice into `AppleLanguages`, which is where iOS keeps a per-app language.
 *
 * The system reads that key when the process starts and hands the result to everything that asks
 * what language this app is in — `NSLocale.preferredLanguages`, the bundle's own lookups, and the
 * locale Compose reports. Nothing re-reads it while the app is running, which is why
 * [appLanguageAppliesOnRestart] is true here and the settings screen says so.
 *
 * [AppLanguage.SYSTEM] removes the key rather than writing the device's current language into it:
 * the point of that entry is to keep following the device, and a tag frozen at the moment the user
 * chose "follow the system" would stop doing exactly that.
 *
 * Unlike the Android actual there is no narrowing of a Traditional Chinese device to `zh-TW` under
 * [AppLanguage.SYSTEM]. It would have to be written into this same key to take effect, and that
 * would freeze the very setting it is applied on behalf of. A reader on `zh-Hant-HK` is served
 * Simplified until they pick 繁體中文 by hand — the one case on this platform where the entry is
 * not merely a preference.
 */
@Composable
actual fun ApplyAppLanguage(language: AppLanguage) {
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

actual val appLanguageAppliesOnRestart: Boolean = true

/** The `NSUserDefaults` key iOS itself reads; the name is the platform's, not ours. */
private const val APPLE_LANGUAGES = "AppleLanguages"
