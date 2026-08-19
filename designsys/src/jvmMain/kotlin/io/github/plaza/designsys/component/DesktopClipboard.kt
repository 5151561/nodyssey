package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

/** The desktop answer to [rememberPlainTextClip]: an AWT [StringSelection]. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun rememberPlainTextClip(): (label: String, text: String) -> ClipEntry =
    remember { { _, text -> ClipEntry(StringSelection(text)) } }

/**
 * Nothing, on purpose.
 *
 * A desktop copy is a keyboard shortcut's worth of interaction and no desktop platform announces
 * one; a toast-shaped popup appearing over a window would be this app inventing a convention rather
 * than following one. The confirmation text still exists — a surface that wants to show it has the
 * string — this is only the answer to "does the OS say so already".
 */
@Composable
internal actual fun rememberCopyConfirmation(): (confirmation: String) -> Unit = remember { {} }
