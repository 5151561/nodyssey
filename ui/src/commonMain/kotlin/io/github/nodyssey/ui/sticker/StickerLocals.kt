package io.github.nodyssey.ui.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.nodyssey.data.sticker.StickerLibrary

/**
 * 我的表情 and the subscribed packs, for the three editors' emoji panels and a post body's long press.
 *
 * Carried as a composition local for the reason 测评对比's basket is: the panel is drawn by three
 * screens and the long press by every post body, none of whose ViewModels has anything else to do
 * with stickers. Null where no shell provided one — a preview, a test — and every reader degrades to
 * the site's own packs.
 */
val LocalStickerLibrary = staticCompositionLocalOf<StickerLibrary?> { null }

/**
 * Where the sticker screens lead: 表情管理 (the panel's gear), its GitHub 订阅 (添加表情's 管理), and
 * 图床设置 for a reader with no host connected yet.
 */
class StickerNavigation(
    val openManager: () -> Unit,
    val openSources: () -> Unit,
    val openImageHost: () -> Unit,
)

val LocalStickerNavigation = staticCompositionLocalOf<StickerNavigation?> { null }

/**
 * [LocalStickerNavigation] for the inside of a sheet: every way out to another screen closes the
 * sheet first.
 *
 * Left open, its saved 「open」 state reopened it whenever the page under it was composed again — and a
 * back gesture on the screen it led to composes that page for the predictive-back preview. The
 * reopened sheet, a window of its own, took the gesture, the preview was dropped, and back never left
 * 图床. Every sheet that can lead somewhere provides this rather than the outer value.
 */
@Composable
internal fun rememberLeavingStickerNavigation(onDismiss: () -> Unit): StickerNavigation? {
    val outer = LocalStickerNavigation.current
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(outer) {
        outer?.let { navigation ->
            StickerNavigation(
                openManager = {
                    dismiss()
                    navigation.openManager()
                },
                openSources = {
                    dismiss()
                    navigation.openSources()
                },
                openImageHost = {
                    dismiss()
                    navigation.openImageHost()
                },
            )
        }
    }
}
