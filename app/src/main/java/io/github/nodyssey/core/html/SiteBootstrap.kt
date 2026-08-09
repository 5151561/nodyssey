package io.github.nodyssey.core.html

import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The base64 `__config__` record NodeSeek server-renders into every page.
 *
 * `<script id="temp-script">` carries the whole signed-in context — a `user` object
 * (`member_id`, `member_name`, `email`, `phone`, `telegram_id`, `coin`, `stardust`, `rank`, `roles`),
 * the category table, `commmentPerPage` — and an inline script decodes it into `window.__config__`
 * and then removes the element.
 *
 * It is not a convenience: several of those values exist nowhere else. The site's own settings page
 * reads the account's email and whether Telegram is bound straight off this object, because no
 * endpoint returns them.
 */
internal object SiteBootstrap {
    private const val ELEMENT_ID = "temp-script"

    /**
     * The decoded JSON text of `__config__`.
     *
     * A page with no bootstrap element is how the site looks to a signed-out visitor, not how a
     * redesign would look — a redesign would still carry *something* where the account was — so that
     * case is [SiteError.LoginRequired] rather than [SiteError.Unparsable].
     */
    fun decode(html: String): String {
        val encoded = encodedText(Jsoup.parse(html))
        if (encoded.isEmpty()) throw SiteException(SiteError.LoginRequired)

        return try {
            decodeBase64(encoded)
        } catch (exception: IllegalArgumentException) {
            throw SiteException(SiteError.Unparsable, exception)
        }
    }

    /**
     * The same payload for callers that can do without it.
     *
     * [decode] speaks for pages whose *only* reason to exist is the account — a settings page with no
     * bootstrap element has failed. A post page has not: it renders for signed-out readers too, and it
     * carries content worth showing whether or not the blob came with it. Those callers want null,
     * not an exception that would throw the article away along with the counts.
     */
    fun decodeOrNull(document: Document): String? {
        val encoded = encodedText(document)
        if (encoded.isEmpty()) return null
        return try {
            decodeBase64(encoded)
        } catch (exception: IllegalArgumentException) {
            null
        }
    }

    private fun encodedText(document: Document): String = document.getElementById(ELEMENT_ID)?.data()?.trim().orEmpty()

    private fun decodeBase64(encoded: String): String = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
}
