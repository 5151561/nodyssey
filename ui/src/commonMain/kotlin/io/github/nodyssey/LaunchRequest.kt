package io.github.nodyssey

import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.ui.navigation.TopLevelDestination

/**
 * Somewhere the app has been told to go by something outside it, to be acted on exactly once.
 *
 * All of these arrive as an `Intent` on Android and all can turn up while the app is already
 * running, which is the only reason they share a type: the composition needs one thing to watch, not
 * several.
 *
 * Here rather than beside the Activity that builds them since step D1: what a launch request *is* is
 * a navigation fact, and the only thing platform-shaped about it is where it arrives from.
 */
sealed interface LaunchRequest {
    /** A notification tap, or the 通知 launcher shortcut. */
    data class OpenTab(val tab: TopLevelDestination) : LaunchRequest

    /** A `nodeseek.com` link from another app. Parsed by `NodeSeekSite.parseInternalRoute`. */
    data class OpenLink(val url: String) : LaunchRequest

    /**
     * 搜索 — the launcher shortcut with [query] null, or a word selected in another app
     * (在 NodeSeek 搜索) with it set.
     */
    data class Search(val query: String?) : LaunchRequest

    /** The 发帖 launcher shortcut: an empty new-post editor, or the one already open. */
    data object OpenComposer : LaunchRequest

    /**
     * Text and pictures another app shared into a new post.
     *
     * [images] are the sender's `content://` addresses, which only stay readable while the grant the
     * share came with lasts — so they ride to the editor in memory and are never written into a
     * back stack key that outlives the process. See `PendingComposerShare`.
     */
    data class ShareToComposer(
        val title: String?,
        val text: String?,
        val images: List<PickedImage>,
    ) : LaunchRequest
}
