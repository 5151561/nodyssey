@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.plaza.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/*
 * `NSData` ↔ `ByteArray`, once for every Apple source set in the repository.
 *
 * Foundation hands bytes back as a reference-counted buffer and Kotlin holds them in an array it
 * owns, so crossing between the two is always a copy — there is no view to take. Three places need
 * it (the transport's multipart bodies, the image pickers in `:ui`, the shell's own downloads) and
 * it is four lines each way, which is exactly the size at which three copies quietly stop agreeing
 * about the empty case.
 */

/** Copies the bytes out. Empty in, empty out — `bytes` is null for a zero-length `NSData`. */
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

/** The other direction, for handing Kotlin-held bytes to a Foundation or Photos API. */
fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
    }
