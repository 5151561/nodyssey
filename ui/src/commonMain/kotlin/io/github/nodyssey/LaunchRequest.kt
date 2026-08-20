package io.github.nodyssey

import io.github.nodyssey.ui.navigation.TopLevelDestination

/**
 * Somewhere the app has been told to go by something outside it, to be acted on exactly once.
 *
 * Both arrive as an `Intent` on Android and both can turn up while the app is already running, which
 * is the only reason they share a type: the composition needs one thing to watch, not two.
 *
 * Here rather than beside the Activity that builds them since step D1: what a launch request *is* is
 * a navigation fact, and the only thing platform-shaped about it is where it arrives from.
 */
sealed interface LaunchRequest {
    /** A notification tap. */
    data class OpenTab(val tab: TopLevelDestination) : LaunchRequest

    /** A `nodeseek.com` link from another app. Parsed by `NodeSeekSite.parseInternalRoute`. */
    data class OpenLink(val url: String) : LaunchRequest
}
