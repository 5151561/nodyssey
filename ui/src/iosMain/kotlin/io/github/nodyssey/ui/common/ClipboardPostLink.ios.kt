package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

// Every programmatic read of UIPasteboard shows the system's paste-permission prompt; see the
// expect declaration.
internal actual val clipboardPostPromptSupported: Boolean = false

@Composable
internal actual fun rememberNewClipboardText(): () -> String? = { null }
