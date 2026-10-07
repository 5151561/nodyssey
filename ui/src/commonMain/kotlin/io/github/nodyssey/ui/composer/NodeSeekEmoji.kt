package io.github.nodyssey.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.nodyssey.core.NodeSeekStickers
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.composer_emoji_group_acn
import io.github.nodyssey.ui.resources.composer_emoji_group_chick
import io.github.nodyssey.ui.resources.composer_emoji_group_fluent
import io.github.nodyssey.ui.resources.composer_emoji_group_onion
import io.github.nodyssey.ui.resources.composer_emoji_stickers_pending
import io.github.nodyssey.ui.resources.sticker_manage_title
import io.github.nodyssey.ui.sticker.LocalStickerLibrary
import io.github.nodyssey.ui.sticker.LocalStickerNavigation
import io.github.nodyssey.ui.sticker.StickerPanelHost
import io.github.nodyssey.ui.sticker.folderEntries
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.editor.EmojiEntry
import io.github.plaza.designsys.editor.EmojiGroup
import io.github.plaza.designsys.editor.EmojiPanel
import io.github.plaza.designsys.editor.InlinePicture
import io.github.plaza.designsys.editor.InlinePictures
import io.github.plaza.designsys.image.allowMeteredImage
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource

/**
 * The four groups NodeSeek's own editor offers, and how their previews are fetched.
 *
 * The first three are NodeSeek's image stickers. Their previews come from the site's own
 * `/static/image/sticker/` — the same URLs post bodies already render — while the editor inserts the
 * site's native shortcode (`:ac01:` etc.). Keeping those two concerns separate means a published
 * post still uses NodeSeek's renderer instead of a guessed Markdown image URL.
 *
 * There is a fifth tab on the site's editor, labelled APP, and it is not a sticker group: it is
 * where 投票 and 星辰收款 are inserted from. This app read it as one and filled it with eighteen
 * invented Unicode emoji that the site has never offered, which is the kind of mistake that only
 * shows up when somebody who uses the site looks at it. Those two features belong on the editor's
 * toolbar when they are built, not in the emoji panel.
 *
 * The previews used to ship in `assets/stickers`, which cost 1.7 MB of APK for images the app was
 * downloading anyway the moment a post used one. Fetching them means one Coil-cached copy serves
 * both the panel and the thread, at the price of a first open that needs the network.
 *
 * Which stickers exist is [NodeSeekStickers]'s, not this file's: the renderer needs the same list to
 * turn a received `:ac01:` back into a picture, and a catalogue kept here would have been a
 * catalogue only the composer could see.
 *
 * The panel that draws all this is `:designsys`'s [io.github.plaza.designsys.editor.EmojiPanel] and
 * knows none of it.
 */
val NodeSeekEmojiGroups = listOf(
    EmojiGroup({ stringResource(Res.string.composer_emoji_group_acn) }, stickers(NodeSeekStickers.AC), key = "site:ac"),
    EmojiGroup({ stringResource(Res.string.composer_emoji_group_onion) }, stickers(NodeSeekStickers.YCT), key = "site:yct"),
    EmojiGroup({ stringResource(Res.string.composer_emoji_group_chick) }, stickers(NodeSeekStickers.XHJ), key = "site:xhj"),
    EmojiGroup(
        { stringResource(Res.string.composer_emoji_group_fluent) },
        key = "fluent",
        entries =
        listOf(
            "😀", "😄", "😅", "🤣", "🙂", "😉",
            "😍", "😘", "🤔", "😐", "😴", "😭",
            "😡", "👍", "👎", "🎉", "❤️", "🔥",
        ).map(EmojiEntry::Unicode),
    ),
)

private fun stickers(names: List<String>): List<EmojiEntry.Sticker> =
    names.map { name ->
        EmojiEntry.Sticker(
            name = name,
            shortcode = " :$name: ",
            url = requireNotNull(NodeSeekStickers.urlFor(name)),
        )
    }

/**
 * A sticker preview, fetched from the site.
 *
 * Waived past 仅 Wi-Fi 加载图片 on the same grounds as a tap on a skipped image: opening the panel is
 * the user asking for these, they are a few KB each, and the grid only requests the cells on screen.
 * Respecting the switch here would instead leave a permanently blank panel for anyone whose network
 * never reports NOT_METERED — a VPN tunnel, for instance.
 */
@Composable
fun NodeSeekStickerImage(
    sticker: EmojiEntry.Sticker,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val request = remember(sticker.url) {
        ImageRequest
            .Builder(context)
            .data(sticker.url)
            .allowMeteredImage(true)
            .build()
    }
    // A cell whose preview fails is not an empty cell: it would read as a sticker that exists and
    // draws nothing, and the grid would silently lose a column's worth of them on a bad connection.
    var failed by remember(sticker.url) { mutableStateOf(false) }
    if (failed) {
        ImageFallback(modifier = modifier)
    } else {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            onError = { failed = true },
            modifier = modifier,
        )
    }
}

/**
 * [EmojiPanel] with NodeSeek's groups and its sticker loader already supplied.
 *
 * Three screens open this panel — the post composer, the reply sheet and the message thread — and
 * none of them has an opinion about which stickers a forum has. The wiring is written once here so
 * a change to it cannot land on two of the three.
 */
@Composable
fun NodeSeekEmojiPanel(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    recent: List<String>,
    onRecentChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = LocalStickerNavigation.current
    val panel: @Composable (List<EmojiGroup>) -> Unit = { groups ->
        EmojiPanel(
            groups = groups,
            onInsert = onInsert,
            onBackspace = onBackspace,
            recent = recent,
            onRecentChange = onRecentChange,
            emptyGroupText = stringResource(Res.string.composer_emoji_stickers_pending),
            stickerImage = { sticker, description, imageModifier ->
                NodeSeekStickerImage(sticker, description, imageModifier)
            },
            modifier = modifier,
            onManage = navigation?.openManager,
            manageLabel = stringResource(Res.string.sticker_manage_title),
        )
    }
    // 我的, the subscribed packs and the site's own, in the order 表情管理 keeps; with no library — a
    // preview — the panel is the site's packs alone.
    val library = LocalStickerLibrary.current
    if (library == null) {
        panel(NodeSeekEmojiGroups)
    } else {
        StickerPanelHost(library, NodeSeekEmojiGroups) { groups -> panel(groups) }
    }
}

/**
 * The stickers in an editor's text, for the field to draw as pictures: every `:name:` the site has,
 * and every `![name](url)` whose link is one of 我的表情 or a subscribed pack's.
 *
 * Only those links, because a photo uploaded into the body is written the same way and is not a
 * sticker. A link the library no longer has — a pack unsubscribed since, or the CDN switched after
 * the draft was written — stays the Markdown it is, which is still exactly what will be sent.
 */
@Composable
fun rememberStickerPictures(): InlinePictures {
    val library = LocalStickerLibrary.current
    val mine by remember(library) { library?.mine ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val subscriptions by remember(library) { library?.subscriptions ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val cdn by remember(library) { library?.cdn ?: flowOf(StickerCdnSettings()) }
        .collectAsStateWithLifecycle(StickerCdnSettings())
    return remember(mine, subscriptions, cdn) {
        val known = buildSet {
            mine.forEach { add(it.url) }
            subscriptions.forEach { subscription ->
                subscription.folders.forEach { folder -> folderEntries(folder, cdn).forEach { add(it.url) } }
            }
        }
        InlinePictures(
            find = { text -> findStickers(text, known::contains, NodeSeekStickers::urlFor) },
            image = { url, modifier ->
                NodeSeekStickerImage(EmojiEntry.Sticker(name = "", shortcode = "", url = url), null, modifier)
            },
        )
    }
}

/**
 * The sticker runs in [text], in order: `![alt](url)` where [isSticker] knows the link, and `:name:`
 * where [shortcode] resolves the name — under the same rules the Markdown reader applies, so the
 * editor never shows a picture the post would not. That includes code: a fenced block and a
 * backtick span are sent as written, so whatever is inside them stays the Markdown it is.
 */
internal fun findStickers(
    text: CharSequence,
    isSticker: (String) -> Boolean,
    shortcode: (String) -> String?,
): List<InlinePicture> {
    val found = mutableListOf<InlinePicture>()
    var index = 0
    while (index < text.length) {
        val lineStart = index == 0 || text[index - 1] == '\n'
        if (lineStart && text.startsWith(FENCE, index)) {
            index = fencedBlockEnd(text, index)
            continue
        }
        val next = when (text[index]) {
            '!' -> MARKDOWN_IMAGE.matchAt(text, index)
                ?.takeIf { isSticker(it.groupValues[1]) }
                ?.let { InlinePicture(index, it.range.last + 1, it.groupValues[1]) }

            ':' -> shortcodeAt(text, index, shortcode)

            '`' -> {
                index = codeSpanEnd(text, index)
                continue
            }

            else -> null
        }
        if (next == null) {
            index++
        } else {
            found += next
            index = next.end
        }
    }
    return found
}

/**
 * Just past the fenced block opening at [start]: past its closing fence's line, or the end of the
 * text when it never closes — the reader's rule, under which an unclosed fence runs to the end.
 */
private fun fencedBlockEnd(
    text: CharSequence,
    start: Int,
): Int {
    var line = text.indexOf('\n', start).let { if (it < 0) return text.length else it + 1 }
    while (line < text.length && !text.startsWith(FENCE, line)) {
        line = text.indexOf('\n', line).let { if (it < 0) return text.length else it + 1 }
    }
    if (line >= text.length) return text.length
    return text.indexOf('\n', line).let { if (it < 0) text.length else it + 1 }
}

/**
 * Just past the backtick span opening at [start], closed by a run of the same length within its
 * paragraph; one past the opening run when there is no such close, which leaves it ordinary text.
 */
private fun codeSpanEnd(
    text: CharSequence,
    start: Int,
): Int {
    var run = 0
    while (start + run < text.length && text[start + run] == '`') run++
    val paragraphEnd = text.indexOf("\n\n", start).let { if (it < 0) text.length else it }
    val close = text.indexOf("`".repeat(run), start + run)
    return if (close < 0 || close >= paragraphEnd) start + run else close + run
}

private fun shortcodeAt(
    text: CharSequence,
    start: Int,
    shortcode: (String) -> String?,
): InlinePicture? {
    var close = start + 1
    while (close < text.length && close - start - 1 < MAX_SHORTCODE_LENGTH && text[close].isShortcodeChar()) close++
    if (close >= text.length || text[close] != ':' || close == start + 1) return null
    val url = shortcode(text.substring(start + 1, close)) ?: return null
    return InlinePicture(start, close + 1, url)
}

private fun Char.isShortcodeChar() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "_-+"

private val MARKDOWN_IMAGE = Regex("""!\[[^\]\n]*]\(([^)\s]+)\)""")

private const val FENCE = "```"

/** The Markdown reader's own limit on a shortcode's name. */
private const val MAX_SHORTCODE_LENGTH = 32
