package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nodyssey.data.settings.AppLanguage
import java.util.Locale

/**
 * Sets the JVM's default locale, which is what Compose reports on this platform.
 *
 * Restart-scoped like iOS, and for a duller reason: nothing on the desktop tells a composition that
 * the default locale moved, so the tree on screen keeps the strings it was built with. There is no
 * activity to recreate and no configuration change to hang a recomposition on.
 *
 * [SYSTEM_DEFAULT] is read once, when this file is first touched, so that
 * [AppLanguage.SYSTEM] has something to go back to. The JVM's default is the only record of what the
 * machine was set to, and overwriting it is how that record is lost.
 */
@Composable
actual fun ApplyAppLanguage(language: AppLanguage) {
    LaunchedEffect(language) {
        Locale.setDefault(language.tag?.let(Locale::forLanguageTag) ?: SYSTEM_DEFAULT)
    }
}

actual val appLanguageAppliesOnRestart: Boolean = true

private val SYSTEM_DEFAULT: Locale = Locale.getDefault()
