package io.github.nodyssey.ui.sticker

import io.github.nodyssey.data.sticker.MINE_GROUP_KEY
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.StickerGroupLayout
import io.github.nodyssey.data.sticker.StickerSubscription
import io.github.nodyssey.data.sticker.SubscribedFolder
import io.github.nodyssey.data.sticker.folderGroupKey
import io.github.plaza.designsys.editor.EmojiGroup

/**
 * One of the panel's groups as 表情管理 lists them (1g): 我的, a subscribed folder, or one of the
 * site's own packs — three kinds in one list, with one set of operations.
 */
internal sealed interface StickerGroupItem {
    val key: String
    val hidden: Boolean
    val count: Int

    data class Mine(
        val stickers: List<MySticker>,
        override val hidden: Boolean,
    ) : StickerGroupItem {
        override val key: String get() = MINE_GROUP_KEY
        override val count: Int get() = stickers.size
    }

    data class Folder(
        val subscription: StickerSubscription,
        val folder: SubscribedFolder,
    ) : StickerGroupItem {
        override val key: String get() = folderGroupKey(subscription.slug, folder.path)
        override val hidden: Boolean get() = folder.hidden
        override val count: Int get() = folder.files.size

        /** This folder's own share of its repository's update — what 「有更新」 on its row means. */
        val updatable: Boolean get() = folder.pendingFiles != null && folder.pendingFiles != folder.files
    }

    data class Site(
        val group: EmojiGroup,
        override val hidden: Boolean,
    ) : StickerGroupItem {
        override val key: String get() = group.key
        override val count: Int get() = group.entries.size
    }
}

/**
 * Every group, in the reader's order. A new install's order is 我的, then each subscribed folder in
 * subscription order, then [site]'s packs — the order the panel had before it could be rearranged —
 * and [layout] rearranges that; see [StickerGroupLayout.arrange].
 */
internal fun arrangeGroups(
    mine: List<MySticker>,
    subscriptions: List<StickerSubscription>,
    layout: StickerGroupLayout,
    site: List<EmojiGroup>,
): List<StickerGroupItem> {
    val defaults: List<StickerGroupItem> =
        listOf(StickerGroupItem.Mine(mine, hidden = MINE_GROUP_KEY in layout.hidden)) +
            subscriptions.flatMap { subscription -> subscription.folders.map { StickerGroupItem.Folder(subscription, it) } } +
            site.map { StickerGroupItem.Site(it, hidden = it.key in layout.hidden) }
    val byKey = defaults.associateBy { it.key }
    return layout.arrange(defaults.map { it.key }).mapNotNull { byKey[it] }
}

/** [current] with the group keyed [dragged] moved to where [target] is. Unchanged if either is missing. */
internal fun List<String>.moved(
    dragged: String,
    target: String,
): List<String> {
    val from = indexOf(dragged)
    val to = indexOf(target)
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
