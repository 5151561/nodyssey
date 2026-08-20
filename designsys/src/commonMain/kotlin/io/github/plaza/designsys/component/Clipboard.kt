package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/**
 * Copies text and says so.
 *
 * Goes through [LocalClipboard] rather than the platform `ClipboardManager`: the composition-local is
 * substitutable, so a screen test can assert what a copy button put on the clipboard without a real
 * system service. `setClipEntry` suspends, hence the scope — the lambda *this* one returns stays
 * synchronous so its call sites are unaffected; [rememberSilentClipboardCopy] hands the suspension
 * on instead, for the reason written there.
 *
 * The two things this cannot do itself are the two the platforms disagree about. `ClipEntry` is an
 * `expect class` with no common way to build one from plain text, and whether a copy needs to be
 * announced at all is a question about the OS the copy happened on — see [rememberPlainTextClip] and
 * [rememberCopyConfirmation].
 */
@Composable
fun rememberClipboardCopy(): (label: String, text: String, confirmation: String) -> Unit {
    val scope = rememberCoroutineScope()
    val write = rememberSilentClipboardCopy()
    val confirm: (String) -> Unit = rememberCopyConfirmation()
    return remember(scope, write, confirm) {
        { label, text, confirmation ->
            // In one coroutine, after the write: the announcement is a claim that the clipboard
            // holds the text, and a claim made before the suspending write returns is one the app
            // cannot back — a cancelled scope would leave the reader told about a copy that never
            // happened. It is the same reason [rememberSilentClipboardCopy] suspends.
            scope.launch {
                write(label, text)
                confirm(confirmation)
            }
        }
    }
}

/**
 * The same copy without the announcement, for a caller that makes its own.
 *
 * A screen showing a snackbar of its own would otherwise say it twice on the versions of Android
 * that leave the announcing to the app — see [rememberCopyConfirmation].
 *
 * Suspends, and that is the point rather than an inconvenience: a caller announcing the copy itself
 * has to be able to do it *after* the write, and a fire-and-forget lambda would leave it launching a
 * second coroutine that races the first. Its own snackbar goes in the same `launch` as this call.
 */
@Composable
fun rememberSilentClipboardCopy(): suspend (label: String, text: String) -> Unit {
    val clipboard = LocalClipboard.current
    val plainText: (String, String) -> ClipEntry = rememberPlainTextClip()
    return remember(clipboard, plainText) {
        { label, text -> clipboard.setClipEntry(plainText(label, text)) }
    }
}

/**
 * Wraps plain text as something the clipboard will take.
 *
 * `ClipEntry` is an `expect class` with no common constructor and no common factory — Compose says
 * what a clipboard entry *is* on every platform but not how to make one, because a `ClipData` and a
 * `Transferable` are not the same kind of thing.
 */
@Composable
internal expect fun rememberPlainTextClip(): (label: String, text: String) -> ClipEntry

/**
 * Tells the reader the copy happened — on the platforms that do not tell them already.
 *
 * "Does the OS already say so" has a different answer per platform, which is the whole reason this
 * is a seam: a shared implementation would have to encode one platform's answer for all of them.
 */
@Composable
internal expect fun rememberCopyConfirmation(): (confirmation: String) -> Unit
