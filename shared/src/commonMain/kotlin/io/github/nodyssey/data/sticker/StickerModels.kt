package io.github.nodyssey.data.sticker

/** One picture in 我的表情. Inserted as `![name](url)`; nothing else about it is stored. */
data class MySticker(
    val url: String,
    val name: String,
)

/** One subscribed repository and the folders taken from it, in the reader's order. */
data class StickerSubscription(
    val owner: String,
    val repo: String,
    val ref: String,
    val pinnedSha: String,
    val checkedAtMillis: Long,
    val folders: List<SubscribedFolder>,
) {
    val slug: String get() = "$owner/$repo"

    /** New pictures across every folder, the number 更新 +n shows. */
    val newCount: Int get() = folders.sumOf { it.newCount }

    /** The branch moved and some folder's list differs — added, renamed or removed pictures alike. */
    val hasUpdate: Boolean get() = folders.any { it.pendingFiles != null && it.pendingFiles != it.files }
}

/** One folder of a subscription — one tab of the panel unless [hidden]. */
data class SubscribedFolder(
    val owner: String,
    val repo: String,
    val pinnedSha: String,
    val path: String,
    val files: List<String>,
    /** The folder at the newer commit the last check found; null when there is none. */
    val pendingFiles: List<String>?,
    val hidden: Boolean,
) {
    val slug: String get() = "$owner/$repo"
    val name: String get() = folderName(path, repo)

    /** Pictures at the newer commit that this folder does not have yet. */
    val newCount: Int get() = pendingFiles?.let { pending -> (pending.toSet() - files.toSet()).size } ?: 0
}

/**
 * Where a subscribed picture is loaded from — and so what link a post carries.
 *
 * jsDelivr is the default because it is the one most often reachable from where this forum's readers
 * are; `raw.githubusercontent.com` is the backup it usually is not. [CUSTOM] takes any mirror that
 * answers jsDelivr's `/gh/owner/repo@commit/path` shape.
 */
enum class StickerCdn(val id: String) {
    JSDELIVR("jsdelivr"),
    GITHUB_RAW("raw"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromId(id: String?): StickerCdn = entries.firstOrNull { it.id == id } ?: JSDELIVR
    }
}

data class StickerCdnSettings(
    val cdn: StickerCdn = StickerCdn.JSDELIVR,
    /** e.g. `https://fastly.jsdelivr.net/gh/`. Blank until the reader fills one in. */
    val customBase: String = "",
) {
    /** The CDN links are actually built with: a custom one with no address falls back to jsDelivr. */
    val effective: StickerCdn
        get() = if (cdn == StickerCdn.CUSTOM && normalizedCustomBase() == null) StickerCdn.JSDELIVR else cdn

    fun normalizedCustomBase(): String? {
        val trimmed = customBase.trim()
        if (!trimmed.startsWith("https://") || trimmed.length <= "https://".length) return null
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * The link for one file of a subscribed repository, pinned to [sha] so a later rename or
     * deletion upstream never breaks a post that already used it.
     */
    fun urlFor(
        owner: String,
        repo: String,
        sha: String,
        path: String,
        cdn: StickerCdn = effective,
    ): String {
        val encoded = path.split('/').joinToString("/") { encodeSegment(it) }
        return when (cdn) {
            StickerCdn.JSDELIVR -> "https://cdn.jsdelivr.net/gh/$owner/$repo@$sha/$encoded"

            StickerCdn.GITHUB_RAW -> "https://raw.githubusercontent.com/$owner/$repo/$sha/$encoded"

            StickerCdn.CUSTOM ->
                normalizedCustomBase()?.let { "$it$owner/$repo@$sha/$encoded" }
                    ?: urlFor(owner, repo, sha, path, StickerCdn.JSDELIVR)
        }
    }
}

/** A file's name without its folder or extension — the label a subscribed sticker carries. */
fun stickerNameFromPath(path: String): String =
    path.substringAfterLast('/').substringBeforeLast('.').ifEmpty { path }

/**
 * Percent-encodes one path segment as UTF-8, leaving RFC 3986's unreserved characters alone.
 *
 * Hand-written because `commonMain` has no URL encoder, and the packs worth subscribing to name
 * their files in Chinese: `熊猫头/摸鱼.gif` has to leave as `%E7%86%8A…` or the Markdown link breaks
 * at the first character a renderer will not take.
 */
fun encodeSegment(segment: String): String =
    buildString {
        segment.encodeToByteArray().forEach { byte ->
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~")) {
                append(ch)
            } else {
                append('%')
                append(HEX[c shr 4])
                append(HEX[c and 0x0F])
            }
        }
    }

private const val HEX = "0123456789ABCDEF"

/**
 * Whether 我的 keeps [url]: `https` only. The app itself cannot draw an `http` picture — Android
 * refuses cleartext by default at this targetSdk and iOS's App Transport Security does the same, and
 * neither shell opts out — so a sticker saved from one would be a broken cell in the panel.
 */
fun isStickerLink(url: String): Boolean = url.startsWith("https://")

/**
 * The `https` links in whatever was pasted, in order and without repeats.
 *
 * Takes a Markdown image's target too, so a line copied out of a post body works as well as a bare
 * link. `http` links are left out on purpose, for [isStickerLink]'s reason.
 */
fun extractStickerLinks(text: String): List<String> =
    LINK.findAll(text).map { it.value.trimEnd('.', ',', ')', '>', '"', '\'') }.distinct().toList()

private val LINK = Regex("""https://[^\s<>"'()\[\]]+""")

/** A label for a pasted link: the file's own name, without extension, short enough for a cell. */
fun stickerNameFromUrl(url: String): String =
    url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        .substringBeforeLast('.').take(MAX_NAME_LENGTH).ifEmpty { "sticker" }

const val MAX_NAME_LENGTH = 20
