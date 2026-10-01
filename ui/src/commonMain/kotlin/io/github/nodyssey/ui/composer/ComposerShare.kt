package io.github.nodyssey.ui.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.nodyssey.data.composer.PickedImage

/** What another app shared into a new post: a title (the sender's subject line), text, pictures. */
data class ComposerShare(
    val title: String?,
    val text: String?,
    val images: List<PickedImage>,
)

/**
 * A share on its way from the intent that brought it to the editor that will hold it.
 *
 * Not a field of `PostComposerKey`, though that would be the obvious way to hand an editor its
 * content: a key is saved with the back stack and restored after process death, and the pictures in
 * a share are `content://` addresses whose read grant dies with the process. A restored key would
 * hand the editor addresses it is no longer allowed to open. So the share is held here, in memory,
 * and the editor takes it exactly once.
 *
 * A state object so the editor's effect re-runs when a second share arrives while it is already open.
 */
class PendingComposerShare {
    var pending: ComposerShare? by mutableStateOf(null)
        private set

    fun offer(share: ComposerShare) {
        pending = share
    }

    /** The share, which is then gone — two editors composed in a row cannot both receive it. */
    fun take(): ComposerShare? = pending.also { pending = null }
}
