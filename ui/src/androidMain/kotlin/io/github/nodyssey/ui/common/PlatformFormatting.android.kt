package io.github.nodyssey.ui.common

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat

@Composable
actual fun rememberFileSizeLabel(bytes: Long): String {
    val context = LocalContext.current
    return remember(context, bytes) { Formatter.formatShortFileSize(context, bytes) }
}

@Composable
actual fun rememberGroupedNumber(value: Long): String {
    // The configuration's locale rather than the JVM default, because that is the one
    // `Resources.getString(id, args)` used to format `%1$,d` with. Through `LocalConfiguration` and
    // not `LocalContext.resources`: only the former recomposes when the configuration changes.
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, value) { NumberFormat.getIntegerInstance(locale).format(value) }
}
