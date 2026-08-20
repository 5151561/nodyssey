package io.github.nodyssey.ui.common

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareText(): (text: String, chooserTitle: String?) -> Unit {
    val context = LocalContext.current
    return { text, chooserTitle ->
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}
