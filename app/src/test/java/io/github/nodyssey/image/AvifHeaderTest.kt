package io.github.nodyssey.image

import coil3.decode.DecodeUtils
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The header sniff, which is the whole of what decides whether the AVIF fallback is offered an
 * image. Every case here is a real encoder's output shape: the brand an AVIF announces itself with
 * is not the same one from file to file, and the format is recognised by the wrong answer to that
 * far more easily than by the right one.
 */
class AvifHeaderTest {
    @Test
    fun `avif as the major brand`() {
        // What libavif itself writes.
        assertTrue(DecodeUtils.isAvif(ftyp("avif", "avif", "mif1", "miaf")))
    }

    @Test
    fun `avif among the compatible brands only`() {
        // What most encoders write, Chrome and Squoosh included: the major brand says nothing more
        // than "a HEIF-style image", and the list beside it is where AVIF is claimed.
        assertTrue(DecodeUtils.isAvif(ftyp("mif1", "mif1", "avif", "miaf")))
    }

    @Test
    fun `an animated sequence is avis`() {
        assertTrue(DecodeUtils.isAvif(ftyp("avis", "avis", "avif", "msf1", "iso8")))
    }

    @Test
    fun `heic is the same container and not this decoder's business`() {
        assertFalse(DecodeUtils.isAvif(ftyp("heic", "mif1", "heic")))
    }

    @Test
    fun `an ftyp box with no brands at all`() {
        assertFalse(DecodeUtils.isAvif(ftyp("mif1")))
    }

    @Test
    fun `a file that is not iso base media`() {
        val png = Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        assertFalse(DecodeUtils.isAvif(png))
    }

    @Test
    fun `a header that ends mid-brand`() {
        val truncated = Buffer().write(ftyp("mif1", "mif1", "avif").readByteArray(14L))
        assertFalse(DecodeUtils.isAvif(truncated))
    }

    @Test
    fun `a box size larger than the file`() {
        // The size is whatever the file claims — here the largest a 32-bit unsigned box size can be,
        // which is also the read that must not be attempted. It ends at the end of the data, false.
        val buffer = Buffer()
        buffer.writeInt(-1)
        buffer.writeUtf8("ftyp")
        buffer.writeUtf8("mif1")
        buffer.writeInt(0)
        buffer.writeUtf8("heic")
        assertFalse(DecodeUtils.isAvif(buffer))
    }

    @Test
    fun `the bytes are left where the decoder will need them`() {
        val source = ftyp("avif", "avif", "mif1")
        val before = source.size

        assertTrue(DecodeUtils.isAvif(source))

        assertEquals(before, source.size)
    }

    /**
     * An `ftyp` box: size, the literal, the major brand, a minor version, then the compatible
     * brands — followed by enough of a tail that the source is never exactly its own header.
     */
    private fun ftyp(majorBrand: String, vararg compatibleBrands: String): Buffer {
        val buffer = Buffer()
        buffer.writeInt(16 + 4 * compatibleBrands.size)
        buffer.writeUtf8("ftyp")
        buffer.writeUtf8(majorBrand)
        buffer.writeInt(0)
        compatibleBrands.forEach { buffer.writeUtf8(it) }
        buffer.write(ByteArray(64))
        return buffer
    }
}
