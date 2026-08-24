package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nodyssey.data.settings.AppLanguage
import java.util.Locale
import java.util.prefs.Preferences

/**
 * Sets the JVM's default locale, which on this platform is both halves at once.
 *
 * Unlike Android and iOS there is nothing else here: `ProvideAppLanguage` is a no-op on the desktop
 * — there is no configuration to invalidate Compose Resources' environment with — so the language a
 * composition draws in *is* `Locale.getDefault()`, alongside the date and number formats. Which
 * makes the timing the whole problem. A `LaunchedEffect` runs after the tree below has already
 * resolved its strings, so a locale set only there is a launch late, every launch, forever: nothing
 * persists across a restart but the settings store, and the store is read asynchronously too.
 *
 * Hence [DesktopAppLanguage]: the choice is mirrored into [Preferences], which is synchronous, and
 * put in force by [applyStored] as this file is initialised — the first call into it is
 * `ApplyAppLanguage` from `NodysseyRoot`, ahead of everything the theme draws. Changing the setting
 * still waits for the next launch, which is what `appLanguageAppliesOnRestart` says.
 */
@Composable
actual fun ApplyAppLanguage(language: AppLanguage?) {
    // Unconditional and ahead of the null check: this is what puts the *stored* choice in force,
    // and it has to land on the first composition rather than in the effect below, which runs
    // after the tree has already read its strings.
    DesktopAppLanguage.applyStored()
    // Null means the store has not answered yet; see the note on the expect. The line above has
    // already put the last launch's answer in force, so there is nothing to do until it speaks.
    if (language == null) return
    LaunchedEffect(language) { DesktopAppLanguage.set(language) }
}

/**
 * The machine's own locale, read before anything below can overwrite it.
 *
 * The JVM default is the only record of what the machine was set to, so it is captured here for
 * [AppLanguage.SYSTEM] to go back to.
 */
private val SYSTEM_DEFAULT: Locale = Locale.getDefault()

/** 语言, as this platform mirrors and applies it. */
private object DesktopAppLanguage {
    private const val KEY_LANGUAGE = "app_language"

    private val preferences: Preferences = Preferences.userRoot().node("io/github/nodyssey/settings")

    /** What is in force, starting from whatever the last launch wrote. */
    private var current: AppLanguage = AppLanguage.ofName(preferences.get(KEY_LANGUAGE, null))

    private var applied = false

    /** Puts [current] in force, once per process. */
    fun applyStored() {
        if (applied) return
        applied = true
        apply(current)
    }

    /** Records a new choice and puts it in force; the tree on screen keeps its old strings. */
    fun set(language: AppLanguage) {
        if (language == current) return
        current = language
        preferences.put(KEY_LANGUAGE, language.name)
        apply(language)
    }

    private fun apply(language: AppLanguage) =
        Locale.setDefault(language.tag?.let(Locale::forLanguageTag) ?: SYSTEM_DEFAULT)
}
