package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/**
 * Whether a screen reader is exploring the UI by touch — TalkBack on Android, VoiceOver on iOS.
 *
 * For the few behaviours that only make sense under direct touch: hiding the tab bar on scroll
 * assumes the reader can flick it back with the same gesture that hid it, which a screen-reader
 * user cannot — their swipes move accessibility focus, and a bar that left the screen simply
 * stops existing for them.
 *
 * An `expect` because Compose has no common answer: the state lives with each platform's
 * accessibility service, and so does the callback that says it changed.
 */
@Composable
internal expect fun rememberTouchExplorationEnabled(): Boolean

/**
 * Whether the reader has asked the OS to remove animations — Android's 移除动画, iOS's Reduce
 * Motion.
 *
 * Compose deliberately ignores the platform animator scale (its animations are not Animators), so
 * honouring the setting is on the app: the theme swaps its motion scheme for snap specs when this
 * is true.
 */
@Composable
internal expect fun rememberReducedMotionEnabled(): Boolean
