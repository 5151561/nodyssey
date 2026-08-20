package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

/**
 * The iOS answer to [rememberPlainTextClip]: Compose's own plain-text entry.
 *
 * `ClipEntry`'s constructor is internal on this platform — an entry wraps `NSItemProvider`s, which
 * Compose builds itself — so unlike the other two targets there is nothing to hand it. The label is
 * dropped rather than encoded somewhere: `UIPasteboard` has no per-item label, and Android's is only
 * ever read by Android's own paste UI.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun rememberPlainTextClip(): (label: String, text: String) -> ClipEntry =
    remember { { _, text -> ClipEntry.withPlainText(text) } }

/**
 * Nothing, for the same reason as desktop and a different mechanism.
 *
 * iOS says nothing about a copy either — there is no system confirmation the way Android 13 has one
 * — but it also gives the app nothing shaped like a toast to say it with. A surface that wants the
 * reader told still has the confirmation string and its own snackbar; this is only the answer to
 * "does the OS say so already", and on iOS it does not.
 */
@Composable
internal actual fun rememberCopyConfirmation(): (confirmation: String) -> Unit = remember { {} }
