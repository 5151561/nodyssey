package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.TextNode
import io.github.nodyssey.core.NodeSeekSite

/**
 * Parses a page NodeSeek served, and undoes the one rewrite Cloudflare performs on the way out.
 *
 * Every page parser goes through here rather than calling Ksoup directly, so that a new parser gets
 * the repair without having to know it exists.
 */
internal object SiteHtml {

    fun parse(html: String): Document = Ksoup.parse(html, NodeSeekSite.BASE_URL).also(::restoreEmails)

    /**
     * Cloudflare's Email Address Obfuscation, undone.
     *
     * The site is behind Cloudflare with that feature on, so an email address an author typed
     * reaches us as `<span class="__cf_email__" data-cfemail="…">[email&#160;protected]</span>` —
     * the address itself is only in the attribute, XOR'd against its own first byte. A browser never
     * shows the placeholder because Cloudflare injects `/cdn-cgi/scripts/…/email-decode.min.js`,
     * which swaps every one of those elements back for the address before the reader sees the page.
     * We render the markup natively and run none of the page's JavaScript, so without this the
     * placeholder is what the reader gets — which is why every address in the app read
     * `[email protected]` while the same post on the web showed the address as plain text.
     *
     * The same feature rewrites `mailto:` hrefs, so both of Cloudflare's shapes are undone here in
     * the order its own script undoes them: the href first, then the element itself. An address the
     * author wrote as a link carries both, and ends up as text either way — the placeholder is what
     * the anchor says, and an anchor reading `[email protected]` is worth less than the address.
     */
    private fun restoreEmails(document: Document) {
        for (anchor in document.select("a[href]")) {
            val href = anchor.attr("href")
            if (!href.startsWith(EMAIL_PROTECTION_PATH)) continue
            val address = decodeCfEmail(href.substringAfter('#', missingDelimiterValue = "")) ?: continue
            // The author's own link text is kept: this branch is the `[联系我](mailto:…)` case, where
            // the address is the destination rather than the words on the page.
            anchor.attr("href", "mailto:$address")
        }
        for (element in document.select("[data-cfemail]")) {
            val address = decodeCfEmail(element.attr("data-cfemail")) ?: continue
            // Replaced by bare text, not by a `mailto:` link, because that is what the site's own
            // page ends up as: the authors write the address in running text, and Cloudflare's
            // script puts a text node back exactly where the placeholder stood.
            element.replaceWith(TextNode(address))
        }
    }

    /** What Cloudflare puts in an obfuscated `mailto:` href, with the payload in the fragment. */
    private const val EMAIL_PROTECTION_PATH = "/cdn-cgi/l/email-protection"

    /**
     * `data-cfemail` is hex: the first byte is the key, every byte after it is one byte of the
     * address XOR'd with that key. Bytes rather than chars because the payload is UTF-8 — the
     * site's script reads it the same way, and an address on a non-ASCII domain would otherwise
     * come back as mojibake.
     *
     * @return null for anything that is not a well-formed payload, which leaves the element alone
     *   rather than putting a garbled string where an address was.
     */
    private fun decodeCfEmail(hex: String): String? {
        if (hex.length < 4 || hex.length % 2 != 0) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

        val key = hex.substring(0, 2).toInt(16)
        val bytes = ByteArray((hex.length - 2) / 2) { index ->
            val at = 2 + index * 2
            (hex.substring(at, at + 2).toInt(16) xor key).toByte()
        }
        return bytes.decodeToString()
    }
}
