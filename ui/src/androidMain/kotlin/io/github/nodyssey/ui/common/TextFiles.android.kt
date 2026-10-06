package io.github.nodyssey.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Storage Access Framework's `OpenDocument`: no storage permission, and the app sees only the
 * file picked. Any type is offered rather than `application/json` alone — file managers and cloud
 * drives label a `.json` file `application/octet-stream` as often as not, and a filter on the type
 * would grey out the very file the reader saved.
 */
@Composable
internal actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { callback.value(withContext(Dispatchers.IO) { readText(context, uri) }) }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
internal actual fun rememberTextFileSaver(
    fileName: String,
    text: suspend () -> String,
    onSaved: (Boolean) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val produce = rememberUpdatedState(text)
    val callback = rememberUpdatedState(onSaved)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val content = produce.value()
                callback.value(withContext(Dispatchers.IO) { writeText(context, uri, content) })
            }
        }
    }
    return { launcher.launch(fileName) }
}

private fun readText(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            // Read in chunks and stop past the cap, rather than `readBytes` on whatever was picked —
            // a video chosen by mistake would otherwise be pulled into memory whole.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_TEXT_FILE_BYTES) return@use null
            }
            out.toByteArray().decodeToString()
        }
    }.getOrNull()

private fun writeText(
    context: Context,
    uri: Uri,
    text: String,
): Boolean =
    runCatching {
        // "wt": truncate, so saving over a longer old backup leaves no tail of it behind.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } != null
    }.getOrDefault(false)

private const val BUFFER_BYTES = 16 * 1024
