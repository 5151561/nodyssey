package io.github.nodyssey.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A PNG in `cacheDir/share/`, handed out through the app's `FileProvider` with a read grant.
 *
 * The directory is the only one the provider's paths file names (`res/xml/share_paths.xml` in
 * `:app`), so the grant reaches this picture and nothing else the app keeps. It is emptied before
 * each write: the receiving app has read what it was going to by the time the reader shares the next
 * floor, and a cache that grows by a picture per share is a cache nobody clears.
 */
@Composable
actual fun rememberShareImage(dispatchers: AppDispatchers): suspend (image: ImageBitmap, chooserTitle: String?) -> Boolean {
    val context = LocalContext.current
    return remember(context, dispatchers) {
        { image, chooserTitle ->
            runCatchingExceptCancellation {
                val file = withContext(dispatchers.io) { writeSharePng(context, image) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        // The ClipData is what carries the grant through the chooser to the app
                        // finally picked; the flag on its own reaches only the chooser.
                        clipData = ClipData.newRawUri(null, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(
                    Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }.isSuccess
        }
    }
}

private fun writeSharePng(context: Context, image: ImageBitmap): File {
    val directory = File(context.cacheDir, SHARE_DIRECTORY)
    directory.listFiles()?.forEach(File::delete)
    directory.mkdirs()
    val file = File(directory, "nodeseek-floor-${System.currentTimeMillis()}.png")
    // A captured layer comes back as a hardware bitmap on API 28+, whose pixels live on the GPU;
    // copying it to an ordinary one is the documented way to get at them.
    val bitmap =
        image.asAndroidBitmap().let { captured ->
            if (captured.config == Bitmap.Config.HARDWARE) captured.copy(Bitmap.Config.ARGB_8888, false) else captured
        }
    file.outputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG encode failed" }
    }
    return file
}

/** Spelled again in `:app`'s `res/xml/share_paths.xml`, which is what the provider actually reads. */
private const val SHARE_DIRECTORY = "share"

/** Ignored by PNG, which is lossless; the parameter is required anyway. */
private const val PNG_QUALITY = 100
