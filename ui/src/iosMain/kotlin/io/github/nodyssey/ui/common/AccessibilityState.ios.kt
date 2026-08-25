package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification

@Composable
internal actual fun rememberTouchExplorationEnabled(): Boolean {
    var enabled by remember { mutableStateOf(UIAccessibilityIsVoiceOverRunning()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityVoiceOverStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> enabled = UIAccessibilityIsVoiceOverRunning() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return enabled
}

@Composable
internal actual fun rememberReducedMotionEnabled(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> reduced = UIAccessibilityIsReduceMotionEnabled() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return reduced
}
