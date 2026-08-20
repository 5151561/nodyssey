package io.github.nodyssey.data.offline

import android.os.StatFs
import java.io.File
import java.security.MessageDigest

/**
 * [OfflineFileStore] on this device's filesystem.
 *
 * SHA-256 of the URL is the file name — see the interface for why the *URL* rather than the bytes.
 *
 * In `:app` rather than in `:shared/androidMain`, which is where the OkHttp half of this package
 * went: [fileOf] is the reason. `OfflineImageInterceptor` hands Coil a `File`, and a `File` is
 * exactly what the contract above cannot promise. That method is this class's own, not the
 * interface's, and its one caller is in this module.
 */
class AndroidOfflineFileStore(
    /** `filesDir/offline`, not `cacheDir` — the system may empty a cache directory at will. */
    private val root: File,
) : OfflineFileStore {
    private val images = File(root, "images")

    override fun nameOf(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    override fun hasStored(url: String): Boolean = fileOf(url) != null

    /** The stored file for this URL, or null when this device does not have it. */
    fun fileOf(url: String): File? = File(images, nameOf(url)).takeIf { it.isFile }

    override fun write(
        name: String,
        bytes: ByteArray,
    ): Long {
        images.mkdirs()
        val file = File(images, name)
        file.writeBytes(bytes)
        return file.length()
    }

    override fun sweep(keep: Set<String>) {
        images.listFiles()?.forEach { file -> if (file.name !in keep) file.delete() }
    }

    override fun clear() {
        images.deleteRecursively()
    }

    /**
     * A reading of exactly zero is folded into "will not say" on purpose. It is what a `StatFs` that
     * cannot model this path answers, and it cannot be told apart from a volume that genuinely has
     * nothing left — so the app declines to guess and lets the write itself be the authority, which
     * it has to be anyway: this class cannot reserve the space between the check and the write.
     */
    override fun freeBytes(): Long? =
        runCatching {
            root.mkdirs()
            StatFs(root.path).availableBytes.takeIf { it > 0 }
        }.getOrNull()

    override fun hasRoomFor(bytes: Long): Boolean {
        val free = freeBytes() ?: return true
        return free - bytes > OfflineFileStore.SPACE_FLOOR_BYTES
    }

    companion object {
        /**
         * Opens the one directory downloads live in.
         *
         * A factory rather than a shared instance because the class holds nothing but that path:
         * the dependency graph and the image loader each build their own, and there is no state for
         * the two to disagree about.
         */
        fun of(filesDir: File) = AndroidOfflineFileStore(File(filesDir, "offline"))
    }
}
