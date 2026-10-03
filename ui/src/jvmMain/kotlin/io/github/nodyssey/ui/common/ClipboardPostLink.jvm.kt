package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

internal actual val clipboardPostPromptSupported: Boolean = false

@Composable
internal actual fun rememberNewClipboardText(): () -> String? = { null }
