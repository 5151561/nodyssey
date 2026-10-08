package io.github.nodyssey.ui.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.nodyssey.data.sticker.StickerLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Where the sticker screens lead: 表情管理 (the panel's gear), its GitHub 订阅 (添加表情's 管理),
 * 添加表情 itself, and 图床设置 for a reader with no host connected yet.
 *
 * [openAdd] takes the tab to open on and, for 换文件夹, the subscribed repository to re-pick.
 */
class StickerNavigation(
    val openManager: () -> Unit,
    val openSources: () -> Unit,
    val openImageHost: () -> Unit,
    val openAdd: (tab: StickerAddTab, source: String?) -> Unit,
    val notices: StickerNotices,
)

/**
 * What 添加表情 says when it finishes, held for the page that opened it.
 *
 * 添加表情 is a page of its own and pops itself on success; the 「已添加 · 撤销」 line belongs under the
 * panel or list the reader is going back to, which is composed again only after the pop. One slot
 * per tab stack: a newer add replaces an older one nobody took.
 */
class StickerNotices {
    private val pending = MutableStateFlow<StickerNotice?>(null)
    val notice: StateFlow<StickerNotice?> = pending.asStateFlow()

    fun post(notice: StickerNotice) {
        pending.value = notice
    }

    /** Hands over the pending notice, if it is still [notice] — two takers cannot both get it. */
    fun take(notice: StickerNotice): Boolean = pending.compareAndSet(notice, null)

    fun clear() {
        pending.value = null
    }
}

/**
 * Shows whatever 添加表情 left for this page in [show], once. Called by every page that opens it.
 */
@Composable
internal fun CollectStickerNotices(show: (StickerNotice) -> Unit) {
    val notices = LocalStickerNavigation.current?.notices ?: return
    val pending by notices.notice.collectAsState()
    val current by rememberUpdatedState(show)
    LaunchedEffect(pending) {
        pending?.let { if (notices.take(it)) current(it) }
    }
}

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
                openAdd = { tab, source ->
                    dismiss()
                    navigation.openAdd(tab, source)
                },
                notices = navigation.notices,
            )
        }
    }
}
