package io.github.nodyssey.image

import coil3.decode.DecodeUtils
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import java.io.EOFException

/**
 * True when these bytes begin an AVIF file.
 *
 * AVIF is ISO base media format, so the answer is in the `ftyp` box every such file opens with:
 * four bytes of box size, the literal `ftyp`, the major brand, a minor version, and then the list of
 * compatible brands. `avif` (a still image) or `avis` (an image sequence) appearing as *either* the
 * major brand or one of the compatible brands is what makes a file AVIF — the major brand alone is
 * not enough, because most encoders write `mif1` there and put `avif` in the list beside it.
 *
 * Read through [BufferedSource.peek] so the bytes stay in the stream for whoever decodes them.
 */
fun DecodeUtils.isAvif(source: BufferedSource): Boolean {
    if (!source.rangeEquals(BOX_TYPE_OFFSET, FTYP)) return false
    val header = source.peek()
    return try {
        // Unsigned, because a size with the high bit set is a size, not a negative number. A box
        // size of 1 means the real size follows in eight more bytes, which no `ftyp` box has ever
        // used; it lands below the 16 the compatible-brand loop needs and reads as "major brand
        // only", which is the safe half of the answer rather than a wrong one.
        val boxSize = header.readInt().toLong() and 0xFFFFFFFFL
        header.skip(BOX_TYPE_BYTES)
        if (header.readByteString(BRAND_BYTES) in AVIF_BRANDS) return true
        header.skip(MINOR_VERSION_BYTES)
        // Bounded rather than trusted: the size is whatever the file says, and this walks a peeked
        // stream that buffers every byte it is asked for. Sixteen brands is far past what any
        // encoder writes.
        var remaining = (boxSize - FTYP_HEADER_BYTES).coerceAtMost(MAX_COMPATIBLE_BRAND_BYTES)
        while (remaining >= BRAND_BYTES) {
            if (header.readByteString(BRAND_BYTES) in AVIF_BRANDS) return true
            remaining -= BRAND_BYTES
        }
        false
    } catch (_: EOFException) {
        // A file that ends inside its own header is not one this decoder can do anything with.
        false
    }
}

/** `ftyp` sits at offset 4; the four bytes before it are the box's size. */
private const val BOX_TYPE_OFFSET = 4L
private const val BOX_TYPE_BYTES = 4L
private const val BRAND_BYTES = 4L
private const val MINOR_VERSION_BYTES = 4L

/** Size, type, major brand and minor version — everything before the compatible-brand list. */
private const val FTYP_HEADER_BYTES = 16L
private const val MAX_COMPATIBLE_BRAND_BYTES = 64L

private val FTYP = "ftyp".encodeUtf8()

/** `avif` is a still image, `avis` an image sequence. Both are what this decoder is for. */
private val AVIF_BRANDS = setOf("avif".encodeUtf8(), "avis".encodeUtf8())
