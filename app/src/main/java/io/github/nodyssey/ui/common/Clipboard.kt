package io.github.nodyssey.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Copies text and says so.
 *
 * Android 13 grew its own copy confirmation popup, so showing a toast as well would double it. The
 * toast is therefore only for the versions that stay silent — without it, tapping "复制" on an older
 * phone gives no feedback at all and reads as a broken button.
 */
@Composable
fun rememberClipboardCopy(): (label: String, text: String, confirmation: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { label, text, confirmation ->
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
