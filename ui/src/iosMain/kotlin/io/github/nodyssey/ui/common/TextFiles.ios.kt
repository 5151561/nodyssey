@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * `UIDocumentPickerViewController` opening a copy: Files hands the app its own copy of the one file
 * chosen, so there is no security-scoped access to start and stop. JSON and plain text only — the
 * export writes `.json`, and a backup pasted into a note and saved from there is text.
 */
@Composable
internal actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)? {
    val callback = rememberUpdatedState(onPicked)
    // Held by composition: the picker keeps its delegate weakly.
    val delegate = remember { DocumentPickerDelegate { urls -> callback.value(urls.firstOrNull()?.let(::readText)) } }
    return remember(delegate) {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText), asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            topmostViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/**
 * Writes the text to a file in the temporary directory and has `UIDocumentPickerViewController`
 * export a copy of it wherever the reader picks — Files' own 存储到 sheet.
 */
@Composable
internal actual fun rememberTextFileSaver(
    fileName: String,
    text: suspend () -> String,
    onSaved: (Boolean) -> Unit,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val produce = rememberUpdatedState(text)
    val callback = rememberUpdatedState(onSaved)
    val delegate = remember { DocumentPickerDelegate { urls -> callback.value(urls.isNotEmpty()) } }
    return remember(fileName, delegate) {
        {
            scope.launch {
                val file = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(fileName)
                val data = NSString.create(string = produce.value()).dataUsingEncoding(NSUTF8StringEncoding)
                if (file == null || data == null || !data.writeToURL(file, atomically = true)) {
                    callback.value(false)
                    return@launch
                }
                val picker = UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)
                picker.delegate = delegate
                topmostViewController()?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

private class DocumentPickerDelegate(
    private val onPicked: (List<NSURL>) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    // Backing out is not a result: the callers say nothing when the reader changes their mind.
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

private fun readText(url: NSURL): String? {
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    if (data.length.toLong() > MAX_TEXT_FILE_BYTES) return null
    return NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
}
