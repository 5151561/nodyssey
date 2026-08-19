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
 * system service. `setClipEntry` suspends, hence the scope — the returned lambda stays synchronous so
 * call sites are unaffected.
 *
 * The two things this cannot do itself are the two the platforms disagree about. `ClipEntry` is an
 * `expect class` with no common way to build one from plain text, and whether a copy needs to be
 * announced at all is a question about the OS the copy happened on — see [rememberPlainTextClip] and
 * [rememberCopyConfirmation].
 */
@Composable
fun rememberClipboardCopy(): (label: String, text: String, confirmation: String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val plainText: (String, String) -> ClipEntry = rememberPlainTextClip()
    val confirm: (String) -> Unit = rememberCopyConfirmation()
    return remember(clipboard, scope, plainText, confirm) {
        { label, text, confirmation ->
            scope.launch {
                clipboard.setClipEntry(plainText(label, text))
                confirm(confirmation)
            }
        }
    }
}
