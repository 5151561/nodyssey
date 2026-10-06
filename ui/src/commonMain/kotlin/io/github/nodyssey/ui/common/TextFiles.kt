package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/**
 * Opens the platform's document picker for one text file and hands [onPicked] what it holds — null
 * when it could not be read or is larger than [MAX_TEXT_FILE_BYTES]. Nothing is called when the
 * reader backs out.
 *
 * Null where the platform has no picker wired up (the desktop JVM), so a caller can leave the option
 * off rather than offer a button that does nothing. Registered while the screen is alive, like
 * `rememberImagePicker`, hence a lambda handed back rather than a function to call.
 */
@Composable
internal expect fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)?

/**
 * Lets the reader choose where a text file named [fileName] goes and writes [text] there; [onSaved]
 * says whether it was written. Not called when the reader backs out. Null where the platform has no
 * way to ask, as with [rememberTextFilePicker].
 *
 * [text] is asked for once the place is chosen rather than handed over up front: nothing has to be
 * held across the system's save screen, which the activity may not survive.
 */
@Composable
internal expect fun rememberTextFileSaver(
    fileName: String,
    text: suspend () -> String,
    onSaved: (Boolean) -> Unit,
): (() -> Unit)?

/** A backup of a few thousand links is tens of kilobytes; anything near this is not one. */
internal const val MAX_TEXT_FILE_BYTES = 4L * 1024 * 1024
