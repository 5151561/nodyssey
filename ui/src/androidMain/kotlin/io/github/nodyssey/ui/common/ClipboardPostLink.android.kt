package io.github.nodyssey.ui.common

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.plaza.designsys.component.OWN_CLIP_EXTRA

internal actual val clipboardPostPromptSupported: Boolean = true

/**
 * `ClipboardManager`, read in two steps so that most calls never read the clip at all.
 *
 * The description comes first. It is metadata — reading it does not raise Android 12's "pasted from
 * your clipboard" notice — and its timestamp says whether this is the clip already looked at, which
 * on most focus changes it is. Only a new clip that is text, not marked sensitive and not written by
 * this app (see [OWN_CLIP_EXTRA]) is opened, once.
 *
 * The timestamp lives as long as the remembered reader, so a recreated Activity looks at the current
 * clip once more; what keeps that from becoming a second prompt is the stored last-offered link.
 */
@Composable
internal actual fun rememberNewClipboardText(): () -> String? {
    val context = LocalContext.current
    return remember(context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        var examined: Long? = null
        reader@{
            // Android throws rather than answering null on some builds when the app is not focused.
            val description =
                runCatching { clipboard?.primaryClipDescription }.getOrNull() ?: return@reader null
            if (description.timestamp == examined) return@reader null
            examined = description.timestamp
            if (description.isOwnOrSensitive()) return@reader null
            if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) &&
                !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            ) {
                return@reader null
            }
            val item = runCatching { clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) }.getOrNull()
            (item?.text ?: item?.uri)?.toString()
        }
    }
}

private fun ClipDescription.isOwnOrSensitive(): Boolean {
    val extras = extras ?: return false
    if (extras.getBoolean(OWN_CLIP_EXTRA, false)) return true
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
}
