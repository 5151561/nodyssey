package io.github.nodyssey.data.offline

import android.os.StatFs
import java.io.File
import java.security.MessageDigest

/**
 * Where downloaded pictures live, and the only place their names are decided.
 *
 * A file per image URL, named by the SHA-256 of that URL. Content-addressing the *URL* rather than
 * the bytes is what lets [fileOf] answer "do we already have this picture" from the URL alone —
 * which is the question Coil asks, synchronously, for every image the app draws, and which a
 * database round trip could not answer in that position. Two threads embedding the same image
 * therefore share one file; `offline_images` keeps a row each so neither can delete the other's.
 *
 * Nothing here is a cache: files are removed by [sweep] when no row refers to them any more, by
 * [clear] for 清空离线内容, and never by pressure. That is the difference between this directory and
 * Coil's, and the reason 「已离线」 can be said at all.
 */
class OfflineFileStore(
    /** `filesDir/offline`, not `cacheDir` — the system may empty a cache directory at will. */
    private val root: File,
) {
    private val images = File(root, "images")

    fun nameOf(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /** The stored file for this URL, or null when this device does not have it. */
    fun fileOf(url: String): File? = File(images, nameOf(url)).takeIf { it.isFile }

    fun write(
        name: String,
        bytes: ByteArray,
    ): Long {
        images.mkdirs()
        val file = File(images, name)
        file.writeBytes(bytes)
        return file.length()
    }

    /** Deletes every stored file no longer named by [keep]. */
    fun sweep(keep: Set<String>) {
        images.listFiles()?.forEach { file -> if (file.name !in keep) file.delete() }
    }

    fun clear() {
        images.deleteRecursively()
    }

    /**
     * Free space on the volume this directory sits on, or null when the system will not say.
     *
     * Null rather than zero: 离线管理 draws 「可用 3.2 GB」 only when there is a number, and a zero
     * would read as a full disk.
     *
     * A reading of exactly zero is folded into "will not say" on purpose. It is what a `StatFs` that
     * cannot model this path answers, and it cannot be told apart from a volume that genuinely has
     * nothing left — so the app declines to guess and lets the write itself be the authority, which
     * it has to be anyway: [OfflineFileStore] cannot reserve the space between the check and the
     * write.
     */
    fun freeBytes(): Long? =
        runCatching {
            root.mkdirs()
            StatFs(root.path).availableBytes.takeIf { it > 0 }
        }.getOrNull()

    /** Refuses a download that would leave the device with less than this much room. */
    fun hasRoomFor(bytes: Long): Boolean {
        val free = freeBytes() ?: return true
        return free - bytes > SPACE_FLOOR_BYTES
    }

    companion object {
        /**
         * Opens the one directory downloads live in.
         *
         * A factory rather than a shared instance because the class holds nothing but that path:
         * the dependency graph and the image loader each build their own, and there is no state for
         * the two to disagree about.
         */
        fun of(filesDir: File) = OfflineFileStore(File(filesDir, "offline"))

        /**
         * How much of the disk the app declines to be the one to fill.
         *
         * A device with under this much left is already in trouble, and a reader who finds out by
         * way of a failed download is finding out too late — so the engine stops before that rather
         * than after, and 下载失败 · 图片超出剩余空间 says which of the two it was.
         */
        const val SPACE_FLOOR_BYTES = 200L * 1024 * 1024
    }
}
