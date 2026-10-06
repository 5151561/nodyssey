package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/** No document picker on the desktop build yet — the same gap as `rememberImagePicker` there. */
@Composable
internal actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberTextFileSaver(
    fileName: String,
    text: suspend () -> String,
    onSaved: (Boolean) -> Unit,
): (() -> Unit)? = null
