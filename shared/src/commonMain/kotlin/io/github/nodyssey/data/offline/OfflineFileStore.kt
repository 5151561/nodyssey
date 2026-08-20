package io.github.nodyssey.data.offline

/**
 * Where downloaded pictures live, and the only place their names are decided.
 *
 * A file per image URL, named by a digest of that URL. Content-addressing the *URL* rather than the
 * bytes is what lets [hasStored] answer "do we already have this picture" from the URL alone —
 * which is the question the image loader asks, synchronously, for every image the app draws, and
 * which a database round trip could not answer in that position. Two threads embedding the same
 * image therefore share one file; `offline_images` keeps a row each so neither can delete the
 * other's.
 *
 * Nothing here is a cache: files are removed by [sweep] when no row refers to them any more, by
 * [clear] for 清空离线内容, and never by pressure. That is the difference between this directory and
 * the image loader's, and the reason 「已离线」 can be said at all.
 *
 * An interface, and the one part of the download engine that is: a filesystem is the one thing in
 * this package that no two platforms spell the same way. Everything else — the queue, the ordering,
 * what counts as done — is in `RoomOfflineLibrary` and needs no platform at all.
 */
interface OfflineFileStore {
    /** The stored file's name for this URL. Deterministic: the same URL always answers the same name. */
    fun nameOf(url: String): String

    /** Whether this device already holds the picture at [url]. */
    fun hasStored(url: String): Boolean

    /** Writes [bytes] under [name] and returns how many bytes landed. */
    fun write(name: String, bytes: ByteArray): Long

    /** Deletes every stored file no longer named by [keep]. */
    fun sweep(keep: Set<String>)

    fun clear()

    /**
     * Free space on the volume this directory sits on, or null when the system will not say.
     *
     * Null rather than zero: 离线管理 draws 「可用 3.2 GB」 only when there is a number, and a zero
     * would read as a full disk.
     */
    fun freeBytes(): Long?

    /** Refuses a download that would leave the device with less than [SPACE_FLOOR_BYTES] of room. */
    fun hasRoomFor(bytes: Long): Boolean

    companion object {
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
