package io.github.nodyssey.data.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OriginalUploadTest {
    @Test
    fun `a picture a browser draws is kept as it is under its real extension`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        val upload = originalUpload(png, "IMG_0421.HEIC")

        assertEquals("image/png", upload?.mimeType)
        assertEquals("IMG_0421.png", upload?.fileName)
    }

    @Test
    fun `a HEIC photo is left for re-encoding rather than sent as an octet stream`() {
        // The first box of an iPhone photo: size, `ftyp`, major brand `heic`.
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".encodeToByteArray() + ByteArray(16)

        assertNull(originalUpload(heic, "IMG_0421.HEIC"))
    }
}
