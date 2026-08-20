package io.github.nodyssey.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Neither source yet — see `ImagePicker.jvm.kt`. Both buttons are inert rather than hidden, because
 * which ones a platform offers is [AvatarPickerController]'s to say and the screen draws what it is
 * given.
 */
@Composable
internal actual fun rememberAvatarPicker(
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
): AvatarPickerController = remember { AvatarPickerController(takePhoto = {}, pickImage = {}) }
