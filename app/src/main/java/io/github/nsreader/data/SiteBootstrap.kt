package io.github.nsreader.data

import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import org.jsoup.Jsoup
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
     * case is [NodeSeekError.LoginRequired] rather than [NodeSeekError.Unparsable].
     */
    fun decode(html: String): String {
        val encoded =
            Jsoup
                .parse(html)
                .getElementById(ELEMENT_ID)
                ?.data()
                ?.trim()
                .orEmpty()
        if (encoded.isEmpty()) throw NodeSeekException(NodeSeekError.LoginRequired)

        return try {
            String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw NodeSeekException(NodeSeekError.Unparsable, exception)
        }
    }
}
