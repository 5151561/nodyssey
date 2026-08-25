package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/** Nothing to do: a desktop window has no system bars. */
@Composable
internal actual fun SystemBarsMatchTheme(darkTheme: Boolean) = Unit
