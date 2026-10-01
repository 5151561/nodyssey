package io.github.plaza.designsys.component

import android.content.ClipData
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext

/**
 * Marks a clip this app wrote, in its description's extras.
 *
 * So that a reader of the clipboard can tell "the reader copied this somewhere else" from "the
 * reader copied this here a minute ago" without opening the clip — the description is metadata, and
 * reading it neither costs Android 12's "pasted from your clipboard" notice nor the clip's contents.
 * A post link copied from a thread's own menu is the case that matters: offering to open that same
 * thread on the way back into the app would be offering what the reader just left.
 */
const val OWN_CLIP_EXTRA = "io.github.plaza.designsys.OWN_CLIP"

/** The Android answer to [io.github.plaza.designsys.component.rememberPlainTextClip]: a `ClipData`. */
@Composable
internal actual fun rememberPlainTextClip(): (label: String, text: String) -> ClipEntry =
    remember {
        { label, text ->
            val clip = ClipData.newPlainText(label, text)
            clip.description.extras = PersistableBundle().apply { putBoolean(OWN_CLIP_EXTRA, true) }
            ClipEntry(clip)
        }
    }

/**
 * Tells the reader the copy happened — on the versions that do not tell them already.
 *
 * Android 13 grew its own copy confirmation popup, so showing a toast as well would double it. The
 * toast is therefore only for the versions that stay silent — without it, tapping "复制" on an older
 * phone gives no feedback at all and reads as a broken button.
 *
 * Which is exactly why this is a seam: that sentence is about one operating system's versions.
 */
@Composable
internal actual fun rememberCopyConfirmation(): (confirmation: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { confirmation ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
