package io.github.nodyssey.ui.settings

import android.content.Context
import android.content.SharedPreferences
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
actual fun ApplyAppLanguage(language: AppLanguage?) {
    val context = LocalContext.current
    // Null means the store has not answered yet, and there is nothing to apply — see the note on
    // the expect. Acting on it would read as 跟随系统 and undo whatever the reader had chosen.
    if (language == null) return
    LaunchedEffect(context, language) { AndroidAppLanguage.applyLanguage(context, language) }
}

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
 * locale list the platform expects. Note that it would buy nothing on screen even then: the
 * platform applies a per-app language by handing the activity a configuration change, and Compose
 * Resources reads neither the configuration nor the `Context` — see `ProvideAppLanguage`.
 */
object AndroidAppLanguage {
    /**
     * The chosen [AppLanguage], by name, mirrored out of DataStore so that [wrap] can read it.
     *
     * `attachBaseContext` runs before anything asynchronous can have finished, and the settings
     * store is a suspending `Flow` — so the one value the platform needs before the first frame is
     * kept where a synchronous read can get at it. DataStore stays the source of truth; this is a
     * cache of one field, written only after that field has changed.
     *
     * The *choice* and not the tag it resolves to, because [AppLanguage.SYSTEM] resolves to a
     * different tag on a Traditional device than on any other, and the device can change underneath
     * a mirror that was written months ago. Only a composition ever writes here, and a process woken
     * by WorkManager alone never has one — so a stored `zh-TW` would keep posting Traditional
     * notifications to a reader who had since moved their device to English. Storing the choice
     * moves that derivation into [wrap], which runs at every process start.
     */
    private const val PREFERENCES = "app_language"
    private const val KEY_LANGUAGE = "language"

    /**
     * Applies [language], and does nothing when it is already in force.
     *
     * Nothing is what the common case is: this runs on every composition of the root, and only a
     * launch whose stored answer disagrees with the one being applied has any work to do.
     */
    internal fun applyLanguage(context: Context, language: AppLanguage) {
        val preferences = preferences(context)
        if (storedLanguage(preferences) == language) return
        preferences.edit { putString(KEY_LANGUAGE, language.name) }
        setProcessLocales(desiredTag(language))
    }

    /**
     * The base context a shell should hand to `super.attachBaseContext`, carrying the stored locale.
     *
     * Two things happen here and neither of them is the screen — that is `ProvideAppLanguage`, out
     * of composition state. Setting the process default is what a WebView, `Accept-Language` and
     * the number formats read. The returned configuration context is what `:app`'s own
     * `res/values-…` are read through — the notification channel names and bodies, which is all
     * that is left there, and which a process woken by WorkManager alone still has to get right.
     *
     * The tag is derived here rather than read off the mirror, so that a [AppLanguage.SYSTEM] whose
     * answer depends on the device — the Traditional narrowing — is re-derived at every process
     * start, including one that only ever runs a worker.
     */
    fun wrap(base: Context): Context {
        val tag = desiredTag(storedLanguage(preferences(base))) ?: return base
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

    /** The locale list [language] means on this device, for a composition to read a bundle by. */
    internal fun localesFor(language: AppLanguage): LocaleList =
        desiredTag(language)?.let(LocaleList::forLanguageTags) ?: systemLocales()

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

    /** What the mirror holds, with a store nobody has written yet reading as [AppLanguage.SYSTEM]. */
    private fun storedLanguage(preferences: SharedPreferences): AppLanguage =
        AppLanguage.ofName(preferences.getString(KEY_LANGUAGE, null))
}
