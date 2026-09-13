package io.github.nodyssey.core

/**
 * The site's own sticker sets, by the shortcode a body writes them as.
 *
 * NodeSeek's editor inserts `:ac01:` and its renderer turns that back into
 * `/static/image/sticker/ac/01.png`. Both halves of that round trip live here so they cannot drift:
 * the emoji panel offers [AC], [YCT] and [XHJ] to insert, and [urlFor] is what the Markdown renderer
 * passes to `parseMarkdown` so a received `:ac01:` draws the same picture instead of six characters
 * of punctuation. Before this existed only the first half was implemented, which is how 私信 came to
 * let you send a sticker it could not show you.
 *
 * The extension is not derivable from the code — the chick set is png for twelve of its 32 and gif
 * for the rest — so the catalogue is enumerated rather than guessed, and a name that is not in it
 * resolves to null and stays text. That is what keeps `21:30:` and a bare `:)` out of the renderer.
 *
 * URLs are built on demand rather than stored: [NodeSeekSite.BASE_URL] follows the active site, and
 * a map filled at class-init would pin the panel and the thread to whichever forum was selected
 * when the process started.
 */
object NodeSeekStickers {
    /** 阿鲁 — the `ac` set, three blocks of numbering, all png. */
    val AC: List<String> =
        (
            (1..54).map { it.pad(2) } +
                (1001..1040).map(Int::toString) +
                (2001..2055).map(Int::toString)
            ).map { "ac$it" }

    /** 洋葱头 — the `yct` set, all gif. */
    val YCT: List<String> = (1..22).map { "yct${it.pad(3)}" }

    /** 小黄鸡 — the `xhj` set, mixed png and gif. */
    val XHJ: List<String> = (1..32).map { "xhj${it.pad(3)}" }

    /** The twelve 小黄鸡 that are stills; the other twenty are animated. */
    private val XHJ_PNG = setOf(1, 2, 3, 5, 6, 7, 11, 22, 24, 25, 31, 32)

    private val EXTENSIONS: Map<String, String> =
        buildMap {
            AC.forEach { put(it, "png") }
            YCT.forEach { put(it, "gif") }
            (1..32).forEach { put("xhj${it.pad(3)}", if (it in XHJ_PNG) "png" else "gif") }
        }

    /** Every shortcode the site offers, in the order its editor lists them. */
    val ALL: List<String> get() = AC + YCT + XHJ

    /**
     * Where `:name:` points, or null when the site has no such sticker.
     *
     * The name is the shortcode without its colons, as `parseMarkdown` hands it over.
     */
    fun urlFor(name: String): String? {
        val extension = EXTENSIONS[name] ?: return null
        return NodeSeekSite.stickerUrl(
            group = name.takeWhile { !it.isDigit() },
            code = name.dropWhile { !it.isDigit() },
            extension = extension,
        )
    }
}

private fun Int.pad(width: Int): String = toString().padStart(width, '0')
