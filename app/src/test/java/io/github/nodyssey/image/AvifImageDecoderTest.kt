package io.github.nodyssey.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which images the software decoder claims, which is the whole of its cost on a device that does not
 * need it. Decoding itself is native code and cannot run here; that half is the library's own.
 */
@RunWith(RobolectricTestRunner::class)
class AvifImageDecoderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val imageLoader = ImageLoader.Builder(context).build()

    @Test
    fun `an avif on a device with no platform decoder is claimed`() {
        val factory = AvifImageDecoder.Factory(PlatformAvifSupport(sdkInt = 30))

        assertNotNull(factory.create(fetchResult(avifBytes()), Options(context), imageLoader))
    }

    @Test
    fun `anything that is not avif is left alone`() {
        val factory = AvifImageDecoder.Factory(PlatformAvifSupport(sdkInt = 30))
        val png = Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        assertNull(factory.create(fetchResult(png), Options(context), imageLoader))
    }

    @Test
    fun `once the platform has decoded an avif the format is handed back to Coil`() {
        val platform = PlatformAvifSupport(sdkInt = 31)
        val factory = AvifImageDecoder.Factory(platform)

        // Before anything is known the platform is given its turn — which happens inside the decoder,
        // so the decoder still has to be created.
        assertTrue(platform.isUntested)
        assertNotNull(factory.create(fetchResult(avifBytes()), Options(context), imageLoader))

        platform.record(decoded = true)

        assertFalse(platform.isUntested)
        assertTrue(platform.decodesAvif)
        assertNull(factory.create(fetchResult(avifBytes()), Options(context), imageLoader))
    }

    @Test
    fun `a platform that failed once is not asked again`() {
        val platform = PlatformAvifSupport(sdkInt = 31)
        val factory = AvifImageDecoder.Factory(platform)

        platform.record(decoded = false)

        assertFalse(platform.isUntested)
        assertFalse(platform.decodesAvif)
        assertNotNull(factory.create(fetchResult(avifBytes()), Options(context), imageLoader))
    }

    @Test
    fun `below Android 12 there is nothing to ask`() {
        assertFalse(PlatformAvifSupport(sdkInt = 30).isUntested)
        assertTrue(PlatformAvifSupport(sdkInt = 31).isUntested)
    }

    private fun fetchResult(source: Buffer): SourceFetchResult =
        SourceFetchResult(
            source = ImageSource(source, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )

    /** An `ftyp` box that says AVIF, which is as far as [AvifImageDecoder.Factory] ever reads. */
    private fun avifBytes(): Buffer {
        val buffer = Buffer()
        buffer.writeInt(20)
        buffer.writeUtf8("ftyp")
        buffer.writeUtf8("avif")
        buffer.writeInt(0)
        buffer.writeUtf8("mif1")
        buffer.write(ByteArray(64))
        return buffer
    }
}
