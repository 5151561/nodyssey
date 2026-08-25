package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/** No touch to explore: the desktop probe is driven by a pointer, and there is no TalkBack analogue to ask. */
@Composable
internal actual fun rememberTouchExplorationEnabled(): Boolean = false

/** The desktop JVM has no portable reduce-motion setting to read; the probe animates as designed. */
@Composable
internal actual fun rememberReducedMotionEnabled(): Boolean = false
