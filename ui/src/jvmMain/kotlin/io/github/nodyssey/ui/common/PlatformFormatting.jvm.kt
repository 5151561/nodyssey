package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Powers of ten and one decimal, which is what Android's formatter settled on — without the locale
 * awareness, because a desktop build of this app does not exist to ship yet and inventing a locale
 * story for it would be inventing a requirement.
 */
@Composable
actual fun rememberFileSizeLabel(bytes: Long): String =
    remember(bytes) {
        val units = listOf("kB", "MB", "GB", "TB")
        if (bytes < 1_000) {
            "$bytes B"
        } else {
            var value = bytes.toDouble()
            var index = -1
            while (value >= 1_000 && index < units.lastIndex) {
                value /= 1_000
                index++
            }
            val tenths = (value * 10).toLong()
            "${tenths / 10}.${tenths % 10} ${units[index]}"
        }
    }
