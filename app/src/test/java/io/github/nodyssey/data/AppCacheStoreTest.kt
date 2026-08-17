package io.github.nodyssey.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.disk.DiskCache
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 清除缓存 against a real cache directory and a real Coil disk cache.
 *
 * The bug this covers is not subtle logic — it is that the button used to clear the database and
 * nothing else, while the 250 MB of images sitting next to it were what the user was looking at in
 * system settings. So the assertions are about the directory: what the number counts, and what is
 * actually gone afterwards.
 */
@RunWith(RobolectricTestRunner::class)
class AppCacheStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    private lateinit var cacheDirectory: File
    private lateinit var imageLoader: ImageLoader

    private fun store(): AppCacheStore {
        cacheDirectory = temporaryFolder.newFolder("cache")
        imageLoader =
            ImageLoader
                .Builder(ApplicationProvider.getApplicationContext<Context>())
                .diskCache {
                    DiskCache
                        .Builder()
                        .directory(File(cacheDirectory, "coil3_disk_cache").toOkioPath())
                        .build()
                }.build()
        return DefaultAppCacheStore(cacheDirectory, dispatchers) { imageLoader }
    }

    private fun writeCachedImage(bytes: Int) {
        val cache = requireNotNull(imageLoader.diskCache)
        val editor = requireNotNull(cache.openEditor("https://www.nodeseek.com/avatar/1.png"))
        cache.fileSystem.write(editor.data) { write(ByteArray(bytes)) }
        editor.commit()
    }

    private fun writeFile(path: String, bytes: Int) {
        val file = File(cacheDirectory, path)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }

    @Test
    fun `the size counts everything under the cache directory, however deeply it is buried`() =
        runTest {
            val store = store()
            writeFile("WebView/Default/HTTP Cache/Cache_Data/data_0", 4_096)
            writeFile("updates/nodyssey-1.2.0.apk", 2_048)
            writeCachedImage(1_024)

            // Coil writes a journal beside the entry, so the total is only bounded from below.
            assertTrue(store.sizeBytes() >= 4_096 + 2_048 + 1_024)
        }

    @Test
    fun `clearing empties the image cache, the WebView cache and a downloaded APK alike`() =
        runTest {
            val store = store()
            writeFile("WebView/Default/HTTP Cache/Cache_Data/data_0", 4_096)
            writeFile("updates/nodyssey-1.2.0.apk", 2_048)
            writeCachedImage(1_024)

            store.clear()

            assertFalse(File(cacheDirectory, "WebView").exists())
            assertFalse(File(cacheDirectory, "updates").exists())
            // Coil rewrites its journal rather than deleting it, so what is left is that header —
            // kilobytes below what any one of the three was holding.
            assertTrue(store.sizeBytes() < 1_024)
        }

    @Test
    fun `the image cache is emptied through Coil rather than deleted underneath it`() =
        runTest {
            val store = store()
            writeCachedImage(1_024)

            store.clear()

            // Coil's journal is open in this process; the directory has to survive so the next
            // image can be cached without the loader having to notice anything happened.
            val cache = requireNotNull(imageLoader.diskCache)
            assertEquals(0L, cache.size)
            writeCachedImage(512)
            assertTrue(cache.size > 0L)
        }

    @Test
    fun `a cache directory that does not exist yet measures zero rather than failing`() =
        runTest {
            val store = store()
            assertTrue(cacheDirectory.deleteRecursively())

            assertEquals(0L, store.sizeBytes())
        }
}
