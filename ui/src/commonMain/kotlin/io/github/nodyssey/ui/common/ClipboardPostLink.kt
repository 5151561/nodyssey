package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import io.github.nodyssey.core.NodeSeekSite

/**
 * Whether 识别剪贴板中的帖子 exists on this platform — and so whether 设置 shows its switch.
 *
 * Android only. iOS asks the reader for permission on every programmatic paste, so a check run each
 * time the app comes forward would be a system dialog each time the app comes forward; the desktop
 * build has nowhere it would be worth the read.
 */
internal expect val clipboardPostPromptSupported: Boolean

/**
 * Reads the clipboard's text, but only when it holds something this reader has not looked at yet.
 *
 * Null when there is nothing new: the same clip as last time, a clip this app wrote itself, or one
 * that is not text. Called when the window gains focus, which on Android 10+ is the only time an
 * app other than the keyboard is allowed to read the clipboard at all. A no-op where
 * [clipboardPostPromptSupported] is false.
 */
@Composable
internal expect fun rememberNewClipboardText(): () -> String?

/** A thread link found on the clipboard, in the one spelling the app stores and opens. */
internal data class ClipboardPost(val url: String, val postId: Long, val page: Int)

/**
 * The thread worth offering for [text], or null when there is none.
 *
 * Only this site's own thread links count — anything else on the clipboard is somebody else's
 * business, and a space or a notification link is not something a reader copies meaning to come
 * back and read. [lastOffered] is the link the prompt last asked about, in [ClipboardPost.url]'s
 * spelling: asking twice about one link is how a helpful prompt becomes one the reader switches off.
 * Comparing the canonical form rather than the raw text is what makes `http://nodeseek.com/post-1-1`
 * and `https://www.nodeseek.com/post-1-1#3` one link.
 */
internal fun clipboardPostToOffer(text: String?, lastOffered: String?): ClipboardPost? {
    val candidate = text?.take(MAX_CLIPBOARD_SCAN_LENGTH) ?: return null
    val url = WEB_URL.find(candidate)?.value?.trimEnd(*TRAILING_PUNCTUATION) ?: return null
    val route = NodeSeekSite.parseInternalRoute(url) as? NodeSeekSite.InternalRoute.Post ?: return null
    val canonical = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(route.postId, route.page)
    if (canonical == lastOffered) return null
    return ClipboardPost(url = canonical, postId = route.postId, page = route.page)
}

/** A link pasted mid-sentence still counts; a clipboard holding a whole article is not scanned to its end. */
private const val MAX_CLIPBOARD_SCAN_LENGTH = 2_000

private val WEB_URL = Regex("""https?://[^\s<>"'，。）】]+""", RegexOption.IGNORE_CASE)

/** What a sentence leaves stuck to the end of a link it quotes. */
private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ')', ']', '!', '?', ';', ':')
