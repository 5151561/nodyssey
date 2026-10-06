package io.github.nodyssey.ui.sticker

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

/** Where the sticker screens lead: 表情管理, and 图床设置 for a reader with no host connected yet. */
class StickerNavigation(
    val openManager: () -> Unit,
    val openImageHost: () -> Unit,
)

val LocalStickerNavigation = staticCompositionLocalOf<StickerNavigation?> { null }
