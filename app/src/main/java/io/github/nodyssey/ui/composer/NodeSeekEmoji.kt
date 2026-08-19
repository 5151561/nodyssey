package io.github.nodyssey.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.editor.EmojiEntry
import io.github.plaza.designsys.editor.EmojiGroup
import io.github.plaza.designsys.editor.EmojiPanel
import io.github.plaza.designsys.image.allowMeteredImage

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
 * The panel that draws all this is `:designsys`'s [io.github.plaza.designsys.editor.EmojiPanel] and
 * knows none of it.
 */
val NodeSeekEmojiGroups = listOf(
    EmojiGroup({ stringResource(R.string.composer_emoji_group_acn) }, acStickers()),
    EmojiGroup({ stringResource(R.string.composer_emoji_group_onion) }, yctStickers()),
    EmojiGroup({ stringResource(R.string.composer_emoji_group_chick) }, xhjStickers()),
    EmojiGroup(
        { stringResource(R.string.composer_emoji_group_fluent) },
        listOf(
            "😀", "😄", "😅", "🤣", "🙂", "😉",
            "😍", "😘", "🤔", "😐", "😴", "😭",
            "😡", "👍", "👎", "🎉", "❤️", "🔥",
        ).map(EmojiEntry::Unicode),
    ),
)

private fun acStickers(): List<EmojiEntry.Sticker> =
    (
        (1..54).map { it.toString().padStart(2, '0') } +
            (1001..1040).map(Int::toString) +
            (2001..2055).map(Int::toString)
        )
        .map { siteSticker(group = "ac", code = it, extension = "png") }

private fun yctStickers(): List<EmojiEntry.Sticker> =
    (1..22)
        .map { it.toString().padStart(3, '0') }
        .map { siteSticker(group = "yct", code = it, extension = "gif") }

private fun xhjStickers(): List<EmojiEntry.Sticker> =
    (1..32).map { number ->
        val code = number.toString().padStart(3, '0')
        val extension = when (number) {
            1, 2, 3, 5, 6, 7, 11, 22, 24, 25, 31, 32 -> "png"
            else -> "gif"
        }
        siteSticker(group = "xhj", code = code, extension = extension)
    }

private fun siteSticker(
    group: String,
    code: String,
    extension: String,
) = EmojiEntry.Sticker(
    name = group + code,
    shortcode = " :$group$code: ",
    url = NodeSeekSite.stickerUrl(group = group, code = code, extension = extension),
)

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
    val context = LocalContext.current
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
    EmojiPanel(
        groups = NodeSeekEmojiGroups,
        onInsert = onInsert,
        onBackspace = onBackspace,
        recent = recent,
        onRecentChange = onRecentChange,
        emptyGroupText = stringResource(R.string.composer_emoji_stickers_pending),
        stickerImage = { sticker, description, imageModifier ->
            NodeSeekStickerImage(sticker, description, imageModifier)
        },
        modifier = modifier,
    )
}
