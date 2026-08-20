package io.github.nodyssey.ios

import io.github.nodyssey.data.offline.OfflineFileStore
import kotlinx.cinterop.ExperimentalForeignApi
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSURL

/**
 * [OfflineFileStore] on this device's filesystem — the Apple counterpart of `AndroidOfflineFileStore`.
 *
 * SHA-256 of the URL is the file name; see the interface for why the *URL* rather than the bytes.
 * The digest comes from okio rather than from CommonCrypto: `CC_SHA256` is deprecated on this
 * platform and okio's is already on the classpath under Room and Coil, so the choice is between a
 * deprecated C function and a function this app already links.
 *
 * Application Support rather than Caches, which is the same distinction `filesDir` and `cacheDir`
 * draw on Android: iOS may empty a Caches directory whenever it is short of room, and 「已离线」 is a
 * promise this app makes about a thread being readable on a plane.
 */
class IosOfflineFileStore(
    private val root: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : OfflineFileStore {
    private val images = root / "images"

    override fun nameOf(url: String): String = url.encodeUtf8().sha256().hex()

    override fun hasStored(url: String): Boolean = pathOf(url) != null

    /** The stored file for this URL, or null when this device does not have it. */
    fun pathOf(url: String): Path? = (images / nameOf(url)).takeIf { fileSystem.exists(it) }

    override fun write(
        name: String,
        bytes: ByteArray,
    ): Long {
        fileSystem.createDirectories(images)
        val file = images / name
        fileSystem.write(file) { write(bytes) }
        return fileSystem.metadata(file).size ?: bytes.size.toLong()
    }

    override fun sweep(keep: Set<String>) {
        if (!fileSystem.exists(images)) return
        fileSystem.list(images).forEach { file -> if (file.name !in keep) fileSystem.delete(file) }
    }

    override fun clear() {
        fileSystem.deleteRecursively(images, mustExist = false)
    }

    /**
     * A reading of exactly zero is folded into "will not say", the same as on Android: it is what an
     * unreadable volume answers and cannot be told apart from a full one, and the write has to be the
     * authority anyway — nothing here can reserve the space between the check and the write.
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun freeBytes(): Long? =
        runCatching {
            fileSystem.createDirectories(root)
            val attributes =
                NSFileManager.defaultManager.attributesOfFileSystemForPath(root.toString(), error = null)
            (attributes?.get(NSFileSystemFreeSize) as? Number)?.toLong()?.takeIf { it > 0 }
        }.getOrNull()

    override fun hasRoomFor(bytes: Long): Boolean {
        val free = freeBytes() ?: return true
        return free - bytes > OfflineFileStore.SPACE_FLOOR_BYTES
    }

    companion object {
        /**
         * Opens the one directory downloads live in.
         *
         * A factory rather than a shared instance because the class holds nothing but that path —
         * the same shape `AndroidOfflineFileStore.of` has, and for the same reason.
         */
        @OptIn(ExperimentalForeignApi::class)
        fun of(applicationSupport: NSURL): IosOfflineFileStore =
            IosOfflineFileStore(requireNotNull(applicationSupport.path).toPath() / "offline")
    }
}
