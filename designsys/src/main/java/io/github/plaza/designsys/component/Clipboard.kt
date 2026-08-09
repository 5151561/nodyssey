package io.github.plaza.designsys.component

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Copies text and says so.
 *
 * Goes through [LocalClipboard] rather than the platform `ClipboardManager`: the composition-local is
 * substitutable, so a screen test can assert what a copy button put on the clipboard without a real
 * system service. `setClipEntry` suspends, hence the scope — the returned lambda stays synchronous so
 * call sites are unaffected.
 *
 * Android 13 grew its own copy confirmation popup, so showing a toast as well would double it. The
 * toast is therefore only for the versions that stay silent — without it, tapping "复制" on an older
 * phone gives no feedback at all and reads as a broken button.
 */
@Composable
fun rememberClipboardCopy(): (label: String, text: String, confirmation: String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, context, scope) {
        { label, text, confirmation ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
