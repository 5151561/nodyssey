package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/**
 * Nothing to do: the status bar style on iOS comes from the hosting view controller, and the
 * Compose Multiplatform host already derives it from the content behind it.
 */
@Composable
internal actual fun SystemBarsMatchTheme(darkTheme: Boolean) = Unit
