package io.github.nodyssey.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A controller whose two lambdas do nothing, which is the shape the `expect` already names for a
 * platform that has fewer sources than Android does — except here it is neither of them rather than
 * one.
 *
 * Both are the same unfinished work as
 * [io.github.nodyssey.ui.composer.rememberImagePicker]: a picker to present, a camera to present, a
 * `UIImage` to bring across to an [PendingAvatar]'s `ImageBitmap`, and a JPEG to encode at
 * [AVATAR_MAX_EDGE_PX]. The screen draws both buttons either way — the `expect` says so — so what a
 * reader would see is two buttons that do not respond, on a screen no iOS build reaches yet.
 */
@Composable
internal actual fun rememberAvatarPicker(
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
): AvatarPickerController = remember { AvatarPickerController(takePhoto = {}, pickImage = {}) }
