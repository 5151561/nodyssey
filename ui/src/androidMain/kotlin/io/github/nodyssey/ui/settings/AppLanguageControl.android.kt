package io.github.nodyssey.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import io.github.nodyssey.data.settings.AppLanguage
import io.github.nodyssey.data.settings.isTraditionalChineseTag

@Composable
actual fun ApplyAppLanguage(language: AppLanguage) {
    val context = LocalContext.current
    LaunchedEffect(context, language) { AndroidAppLanguage.applyLanguage(context, language) }
}

/** Android recreates the activity under the new locale, so the change is on screen at once. */
actual val appLanguageAppliesOnRestart: Boolean = false

/**
 * 语言, as this platform stores and applies it.
 *
 * Public because `:app` has to reach it: [wrap] belongs in `attachBaseContext`, which is a shell's
 * business and runs long before any composition exists. The rest of this object is the settings
 * screen's half and stays internal.
 *
 * **Why this is hand-rolled.** The platform's own answer is per-app language —
 * `LocaleManager.setApplicationLocales`, which persists the choice in the system, shows it under
 * *Settings › Apps › Language*, and applies it before the process starts. Two things rule it out
 * here. It arrived in API 33 and this app's `minSdk` is 26, and the androidx backport that covers
 * the gap — `AppCompatDelegate.setApplicationLocales` — only applies below 33 through
 * `AppCompatActivity`, which this app does not have and cannot cheaply acquire: it is a
 * `ComponentActivity` under a Material 3 theme, and `AppCompatActivity` requires a `Theme.AppCompat`
 * to inflate at all. The second is [AppLanguage.SYSTEM]: the system's own list has to be *narrowed*
 * to the three bundles this app ships — a `zh-Hant-HK` device has to be read as `zh-TW`, see
 * [isTraditionalChineseTag] — and re-derived at every launch so that changing the device's language
 * still reaches the app, which an override persisted by the system would defeat.
 *
 * Replace all of it with `LocaleManager` on the day `minSdk` reaches 33 *and* Compose Resources can
 * name one bundle from several locale qualifiers, which is what would let `SYSTEM` mean the empty
 * locale list the platform expects.
 */
object AndroidAppLanguage {
    /**
     * The applied tag, mirrored out of DataStore so that [wrap] can read it.
     *
     * `attachBaseContext` runs before anything asynchronous can have finished, and the settings
     * store is a suspending `Flow` — so the one value the platform needs before the first frame is
     * kept where a synchronous read can get at it. DataStore stays the source of truth; this is a
     * cache of one field, written only after that field has changed.
     */
    private const val PREFERENCES = "app_language"
    private const val KEY_TAG = "language_tag"

    /**
     * Applies [language], and does nothing when it is already in force.
     *
     * Nothing is what the common case is: this runs on every composition of the root, and only a
     * launch whose stored answer disagrees with the one being applied has any work to do.
     */
    internal fun applyLanguage(context: Context, language: AppLanguage) {
        val tag = desiredTag(language)
        val preferences = preferences(context)
        if (preferences.getString(KEY_TAG, null) == tag) return
        preferences.edit { if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag) }
        setProcessLocales(tag)
        // The composition on screen was built against the old locale and no amount of recomposing
        // rereads it — `Locale.current` is the process's answer, read once per composition. A
        // recreate is what re-enters `attachBaseContext` and builds the whole tree again.
        context.findActivity()?.recreate()
    }

    /**
     * The base context a shell should hand to `super.attachBaseContext`, carrying the stored locale.
     *
     * Two things happen here and they are for two different resource systems. Setting the process
     * default is what `:ui`'s and `:designsys`' Compose Resources read, since those resolve against
     * `Locale.current` and never see a `Context` at all. The returned configuration context is what
     * `:app`'s own `res/values-…` are read through — the notification channel names and bodies,
     * which is all that is left there.
     */
    fun wrap(base: Context): Context {
        val tag = preferences(base).getString(KEY_TAG, null) ?: return base
        val locales = LocaleList.forLanguageTags(tag)
        LocaleList.setDefault(locales)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(locales)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Which of the three shipped bundles [language] asks for, or null for "leave the device alone".
     *
     * Null is the answer for [AppLanguage.SYSTEM] on every device except one reading Traditional
     * Chinese, which is the single case the resource qualifiers cannot resolve on their own — see
     * [isTraditionalChineseTag]. Not overriding is worth the special case: it leaves
     * `Accept-Language`, the number formats and the web view exactly as the device set them.
     */
    private fun desiredTag(language: AppLanguage): String? {
        language.tag?.let { return it }
        val primary = systemLocales().takeIf { !it.isEmpty }?.get(0) ?: return null
        return AppLanguage.TRADITIONAL_CHINESE.tag.takeIf {
            isTraditionalChineseTag(primary.toLanguageTag())
        }
    }

    private fun setProcessLocales(tag: String?) {
        val locales = tag?.let(LocaleList::forLanguageTags) ?: systemLocales()
        if (!locales.isEmpty) LocaleList.setDefault(locales)
    }

    /**
     * The device's languages, not this app's.
     *
     * `Resources.getSystem()` is the one resource object [wrap] never touches, which is what makes
     * it the honest answer to "what would this device be in if we had not interfered".
     */
    private fun systemLocales(): LocaleList = Resources.getSystem().configuration.locales

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext ?: return null
        }
        return null
    }
}
