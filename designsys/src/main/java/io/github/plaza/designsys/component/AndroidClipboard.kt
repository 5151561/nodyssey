package io.github.plaza.designsys.component

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext

/**
 * Wraps plain text as something the clipboard will take.
 *
 * `ClipEntry` is an `expect class` with no common constructor and no common factory — Compose says
 * what a clipboard entry *is* on every platform but not how to make one, because a `ClipData` and a
 * `UIPasteboard` item are not the same kind of thing. This is the Android answer.
 */
@Composable
internal fun rememberPlainTextClip(): (label: String, text: String) -> ClipEntry =
    remember { { label, text -> ClipEntry(ClipData.newPlainText(label, text)) } }

/**
 * Tells the reader the copy happened — on the versions that do not tell them already.
 *
 * Android 13 grew its own copy confirmation popup, so showing a toast as well would double it. The
 * toast is therefore only for the versions that stay silent — without it, tapping "复制" on an older
 * phone gives no feedback at all and reads as a broken button.
 *
 * Which is exactly why this is not in the shared file: "does the OS already say so" has a different
 * answer per platform, and a shared implementation would have to encode this one phone's.
 */
@Composable
internal fun rememberCopyConfirmation(): (confirmation: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { confirmation ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
